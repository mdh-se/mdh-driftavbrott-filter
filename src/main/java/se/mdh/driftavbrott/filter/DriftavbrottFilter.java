package se.mdh.driftavbrott.filter;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import se.mdh.driftavbrott.client.DriftavbrottFacade;
import se.mdh.driftavbrott.modell.Driftavbrott;

/**
 * Ett <code>Filter</code> som används för att presentera ett felmeddelande
 * när någon försöker komma åt resurser under ett driftavbrott.
 * <p>
 * Här följer ett exempel på hur det kan konfigureras i web.xml:
 * <pre>
  &lt;filter&gt;
    &lt;description&gt;
      Utför en kontroll av att resursen får användas vid tiden för accessen.
    &lt;/description&gt;
    &lt;filter-name&gt;DriftavbrottFilter&lt;/filter-name&gt;
    &lt;filter-class&gt;se.mdh.driftavbrott.filter.DriftavbrottFilter&lt;/filter-class&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        De sökvägar som ska undantas från driftavbrott, t.ex. end-points för
        övervakning. Om man vill undanta flera sökvägar så ska de separeras med
        mellanslag.
      &lt;/description&gt;
      &lt;param-name&gt;excludes&lt;/param-name&gt;
      &lt;param-value&gt;/actuator/health&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        De kanaler som vi ska lyssna på. Om man vill lyssna på flera kanaler
        så ska de separeras med kommatecken.
      &lt;/description&gt;
      &lt;param-name&gt;kanaler&lt;/param-name&gt;
      &lt;param-value&gt;ladok.produktionssattning,ladok.uppgradering&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        Den sida som ska visas om den resurs som filtret skyddar inte ska vara
        tillgänglig vid tiden för accessen.
      &lt;/description&gt;
      &lt;param-name&gt;sida&lt;/param-name&gt;
      &lt;param-value&gt;/WEB-INF/jsp/fel/driftavbrott.jsp&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        ArtifactId för det system som är intresserat av information om
        driftavbrott.
      &lt;/description&gt;
      &lt;param-name&gt;system&lt;/param-name&gt;
      &lt;param-value&gt;mdh-parkering&lt;/param-value&gt;
    &lt;/init-param&gt;
  &lt;/filter&gt;
  ...
  &lt;filter-mapping&gt;
    &lt;filter-name&gt;DriftavbrottFilter&lt;/filter-name&gt;
    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
  &lt;/filter-mapping&gt;
 * </pre>
 * @author Lars Lindqvist
 * @author Dennis Lundberg
 */
public class DriftavbrottFilter implements Filter {
  /**
   * En referens till den log som klassen använder.
   */
  private static final Log log = LogFactory.getLog(DriftavbrottFilter.class);
  /**
   * Namn på ett attribut på driftavbrottsidan som anger nyckeln för
   * felmeddelandet.
   * <p>
   * Observera att värdet av denna inte får innehålla en punkt.
   */
  private static final String ATTRIBUTE_MEDDELANDE_KEY = "meddelande_key";
  private static final String ATTRIBUTE_SLUT = "slut";
  private static final String ATTRIBUTE_START = "start";
  /**
   * Antal millisekunder som vi ska cacha ett driftavbrott.
   */
  private static final int CACHE_MILLIS = 60000;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  /**
   * Namn på en init-parameter, som anger en eller flera context-relativa
   * sökvägar som ska undantas från driftavbrottsfiltret.
   * Om man vill undanta flera sökvägar så ska de separeras med mellanslag.
   */
  private static final String PARAMETER_EXCLUDES = "excludes";
  /**
   * Namn på en init-parameter, som anger kanalerna som vi ska lyssna på.
   * Om man vill lyssna på flera kanaler så ska de separeras med kommatecken.
   */
  private static final String PARAMETER_KANALER = "kanaler";
  /**
   * Namn på den parameter som innehåller den fullständiga sökvägen till
   * driftavbrottsidan som en användare kommer att skickas till om åtkomst
   * till resursen inte är tillåten.
   */
  private static final String PARAMETER_SIDA = "sida";
  /**
   * Namn på en init-parameter, som anger vilket system som vill ha information
   * om driftavbrott, dvs ditt system. Ska anges i form av
   * <code>artifactId</code>.
   */
  private static final String PARAMETER_SYSTEM = "system";
  /**
   * Namn på en init-parameter, som anger den marginal som skall användas vid frågor om driftavbrott.
   * Marginalen anges som en siffra i minuter.
   */
  private static final String PARAMETER_MARGINAL = "marginal";

  /**
   * Pågående driftavbrott.
   */
  private Driftavbrott driftavbrott;
  private List<String> excludes;
  private DriftavbrottFacade facade;
  private String kanaler;
  private long lastFetch = 0;
  /**
   * Den sida som ska ha hand om presentationen.
   */
  private String sida;
  private String system;
  private int marginal;

  /**
   * Städar undan resurser.
   */
  @Override
  public void destroy() {
    driftavbrott = null;
    excludes = null;
    facade = null;
    kanaler = null;
    sida = null;
    system = null;
    marginal = 0;
  }

  /**
   * Utför kontroll av om en resurs är tillåten att använda
   * eller inte baserat på nuvarande tid.
   *
   * @param request Aktuell request
   * @param response Aktuell respons
   * @param filterChain Filterkedja
   * @throws IOException Om ett fel i I/O-hanteringen inträffar
   * @throws ServletException Om filtret inte kan utföras
   */
  @Override
  public void doFilter(final ServletRequest request,
                       final ServletResponse response,
                       final FilterChain filterChain)
      throws IOException, ServletException {
    // Cachning
    if(new Date().getTime() - lastFetch > CACHE_MILLIS) {
      fetchDriftavbrott();
    }

    // Undersök om angiven sökväg ska undantas från driftavbrott
    String path = null;
    if(request instanceof HttpServletRequest) {
      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      path = httpServletRequest.getRequestURI()
          .substring(httpServletRequest.getContextPath().length());
    }
    if(isExcluded(excludes, path)) {
      log.debug("Anropad sökväg '" + path
                    + "' är undantagen från driftavbrott.");
      filterChain.doFilter(request, response);
    }
    else if(isDriftavbrott(driftavbrott)) {
      log.info("Tidpunkten för accessen till sökvägen '" + path
                   + "' begränsas av ett driftavbrottsfilter för kanalen "
                   + driftavbrott.getKanal() + " som är aktivt under tidsperioden: "
                   + DATE_TIME_FORMATTER.format(driftavbrott.getStart()) + " - "
                   + DATE_TIME_FORMATTER.format(driftavbrott.getSlut())
                   + " med en marginal på " + marginal + " minuter.");

      // Om nuvarande tid är utanför intervallet ska en felsida presenteras
      request.setAttribute(ATTRIBUTE_MEDDELANDE_KEY, driftavbrott.getKanal());
      request.setAttribute(ATTRIBUTE_SLUT, DATE_TIME_FORMATTER.format(driftavbrott.getSlut()));
      request.setAttribute(ATTRIBUTE_START, DATE_TIME_FORMATTER.format(driftavbrott.getStart()));

      RequestDispatcher rd = request.getRequestDispatcher(sida);
      rd.forward(request, response);
    }
    else {
      log.debug("Tidpunkten för accessen är tillåten");

      // Om nuvarande tid är inom intervallet tillåt accessen till den tidsskyddade resursen
      filterChain.doFilter(request, response);
    }
  }

  /**
   * Hämta ett pågående driftavbrott från web servicen.
   */
  private void fetchDriftavbrott() throws IOException {
    facade = new DriftavbrottFacade();
    List<String> kanalLista = Arrays.asList(StringUtils.split(kanaler, ","));
    try {
      driftavbrott = facade.getPagaendeDriftavbrott(kanalLista, system, marginal);
      log.debug("Hämtade detta driftavbrott:" + driftavbrott);

      lastFetch = new Date().getTime();
    }
    catch (WebServiceException wse) {
      log.warn("Det gick inte att hämta information om pågående driftavbrott.", wse);
    }
  }

  /**
   * Initierar filtret.
   *
   * @param filterConfig Aktuell konfiguration
   * @throws ServletException Om initieringen inte kan utföras
   */
  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    final boolean debugEnabled = log.isDebugEnabled();

    excludes = parseExcludes(filterConfig.getInitParameter(PARAMETER_EXCLUDES));
    kanaler = StringUtils.defaultString(filterConfig.getInitParameter(PARAMETER_KANALER));
    sida = StringUtils.defaultString(filterConfig.getInitParameter(PARAMETER_SIDA));
    system = StringUtils.defaultString(filterConfig.getInitParameter(PARAMETER_SYSTEM));
    String marginalParameterValue = StringUtils.defaultString(filterConfig.getInitParameter(PARAMETER_MARGINAL));
    if(StringUtils.isNotEmpty(marginalParameterValue)) {
      try {
        marginal = Integer.parseInt(marginalParameterValue);
      }
      catch(NumberFormatException e) {
        log.debug(
            "Kunde inte omvandla filterparameter med namn " + PARAMETER_MARGINAL
                + " och värdet " + marginalParameterValue
                + " till en integer. Sätter värdet till 0.");
        marginal = 0;
      }
    }
    else {
      marginal = 0;
    }

    if (debugEnabled) {
      log.debug("Excludes är '" + excludes + "'.");
      log.debug("Kanaler är '" + kanaler + "'.");
      log.debug("Driftavbrottsida är '" + sida + "'.");
      log.debug("System är '" + system + "'.");
      log.debug("Marginal är '" + marginal + "'.");
    }
  }

  /**
   * Avgör om åtkomst är tillåten eller inte.
   *
   * @return <code>true</code> om åtkomst inte är tillåten, annars <code>false</code>
   */
  private boolean isDriftavbrott(Driftavbrott driftavbrott) {
    return driftavbrott != null;
  }

  /**
   * Kontrollera om en path ska exkluderas från driftavbrott eller inte.
   *
   * @param excludes De sökvägar som ska exkluderas
   * @param path Sökvägen som ska kontrolleras
   * @return <code>true</code> om det finns en exkludering som utgör ett prefix till sökvägen, annars <code>false</code>
   */
  static boolean isExcluded(List<String> excludes, String path) {
    if(path == null) {
      return false;
    }
    for(String exclude : excludes) {
      if(path.startsWith(exclude)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parsa ut eventuella excludes från parametern.
   *
   * @param excludesParameter En mellanslags-separerad String med excludes
   * @return En List som kan vara tom men aldrig <code>null</code>
   */
  static List<String> parseExcludes(String excludesParameter) {
    String[] excludesArray = StringUtils.split(excludesParameter, " ");
    if(excludesArray == null) {
      return new ArrayList<>();
    }
    else {
      return Arrays.asList(excludesArray);
    }
  }
}

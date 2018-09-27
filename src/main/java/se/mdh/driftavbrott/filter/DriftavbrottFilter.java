package se.mdh.driftavbrott.filter;

import java.io.IOException;
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
import javax.xml.ws.WebServiceException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import se.mdh.driftavbrott.client.DriftavbrottFacade;
import se.mdh.driftavbrott.modell.Driftavbrott;

/**
 * Ett <code>Filter</code> som anv�nds f�r att presentera ett felmeddelande
 * n�r n�gon f�rs�ker komma �t resurser under ett driftavbrott.
 * <p>
 * H�r f�ljer ett exempel p� hur det kan konfigureras i web.xml:
 * <pre>
  &lt;filter&gt;
    &lt;description&gt;
      Utf�r en kontroll av att resursen f�r anv�ndas vid tiden f�r accessen.
    &lt;/description&gt;
    &lt;filter-name&gt;DriftavbrottFilter&lt;/filter-name&gt;
    &lt;filter-class&gt;se.mdh.driftavbrott.filter.DriftavbrottFilter&lt;/filter-class&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        De kanaler som vi ska lyssna p�. Om man vill lyssna p� flera kanaler
        s� ska de separeras med kommatecken.
      &lt;/description&gt;
      &lt;param-name&gt;kanaler&lt;/param-name&gt;
      &lt;param-value&gt;ladok.produktionssattning,ladok.uppgradering&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        Den sida som ska visas om den resurs som filtret skyddar inte ska vara
        tillg�nglig vid tiden f�r accessen.
      &lt;/description&gt;
      &lt;param-name&gt;sida&lt;/param-name&gt;
      &lt;param-value&gt;/WEB-INF/jsp/fel/driftavbrott.jsp&lt;/param-value&gt;
    &lt;/init-param&gt;
    &lt;init-param&gt;
      &lt;description&gt;
        ArtifactId f�r det system som �r intresserat av information om
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
 * @version $Id: DriftavbrottFilter.java 49071 2018-02-02 15:10:41Z dlg01 $
 */
public class DriftavbrottFilter implements Filter {
  /**
   * En referens till den log som klassen anv�nder.
   */
  private static final Log log = LogFactory.getLog(DriftavbrottFilter.class);
  /**
   * Namn p� ett attribut p� driftavbrottsidan som anger nyckeln f�r
   * felmeddelandet.
   * <p>
   * Observera att v�rdet av denna inte f�r inneh�lla en punkt.
   */
  private static final String ATTRIBUTE_MEDDELANDE_KEY = "meddelande_key";
  private static final String ATTRIBUTE_SLUT = "slut";
  private static final String ATTRIBUTE_START = "start";
  /**
   * Antal millisekunder som vi ska cacha ett driftavbrott.
   */
  private static final int CACHE_MILLIS = 60000;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
  /**
   * Namn p� en init-parameter, som anger kanalerna som vi ska lyssna p�.
   * Om man vill lyssna p� flera kanaler s� ska de separeras med kommatecken.
   */
  private static final String PARAMETER_KANALER = "kanaler";
  /**
   * Namn p� den parameter som inneh�ller den fullst�ndiga s�kv�gen till
   * driftavbrottsidan som en anv�ndare kommer att skickas till om �tkomst
   * till resursen inte �r till�ten.
   */
  private static final String PARAMETER_SIDA = "sida";
  /**
   * Namn p� en init-parameter, som anger vilket system som vill ha information
   * om driftavbrott, dvs ditt system. Ska anges i form av
   * <code>artifactId</code>.
   */
  private static final String PARAMETER_SYSTEM = "system";
  /**
   * Namn p� en init-parameter, som anger den marginal som skall anv�ndas vid fr�gor om driftavbrott.
   * Marginalen anges som en siffra i minuter.
   */
  private static final String PARAMETER_MARGINAL = "marginal";

  /**
   * P�g�ende driftavbrott.
   */
  private Driftavbrott driftavbrott;
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
   * St�dar undan resurser.
   */
  @Override
  public void destroy() {
    driftavbrott = null;
    facade = null;
    kanaler = null;
    sida = null;
    system = null;
    marginal = 0;
  }

  /**
   * Utf�r kontroll av om en resurs �r till�ten att anv�nda
   * eller inte baserat p� nuvarande tid.
   *
   * @param request Aktuell request
   * @param response Aktuell respons
   * @param filterChain Filterkedja
   * @throws IOException Om ett fel i I/O-hanteringen intr�ffar
   * @throws ServletException Om filtret inte kan utf�ras
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

    if (isDriftavbrott(driftavbrott)) {
      log.info("Tidpunkten f�r accessen begr�nsas av ett driftavbrottsfilter f�r kanalen "
                   + driftavbrott.getKanal() + " som �r aktivt under tidsperioden: "
                   + driftavbrott.getStart().toString(DATE_TIME_FORMATTER) + " - "
                   + driftavbrott.getSlut().toString(DATE_TIME_FORMATTER)
                   + " med en marginal p� " + marginal + " minuter.");

      // Om nuvarande tid �r utanf�r intervallet ska en felsida presenteras
      request.setAttribute(ATTRIBUTE_MEDDELANDE_KEY, driftavbrott.getKanal());
      request.setAttribute(ATTRIBUTE_SLUT, driftavbrott.getSlut().toString(DATE_TIME_FORMATTER));
      request.setAttribute(ATTRIBUTE_START, driftavbrott.getStart().toString(DATE_TIME_FORMATTER));

      RequestDispatcher rd = request.getRequestDispatcher(sida);
      rd.forward(request, response);
    }
    else {
      log.debug("Tidpunkten f�r accessen �r till�ten");

      // Om nuvarande tid �r inom intervallet till�t accessen till den tidsskyddade resursen
      filterChain.doFilter(request, response);
    }
  }

  /**
   * H�mta ett p�g�ende driftavbrott fr�n web servicen.
   */
  private void fetchDriftavbrott() throws IOException {
    facade = new DriftavbrottFacade();
    List<String> kanalLista = Arrays.asList(StringUtils.split(kanaler, ","));
    try {
      driftavbrott = facade.getPagaendeDriftavbrott(kanalLista, system, marginal);
      log.debug("H�mtade detta driftavbrott:" + driftavbrott);

      lastFetch = new Date().getTime();
    }
    catch (WebServiceException wse) {
      log.warn("Det gick inte att h�mta information om p�g�ende driftavbrott.", wse);
    }
  }

  /**
   * Initierar filtret.
   *
   * @param filterConfig Aktuell konfiguration
   * @throws ServletException Om initieringen inte kan utf�ras
   */
  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    final boolean debugEnabled = log.isDebugEnabled();

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
                + " och v�rdet " + marginalParameterValue
                + " till en integer. S�tter v�rdet till 0.");
        marginal = 0;
      }
    } else {
      marginal = 0;
    }

    if (debugEnabled) {
      log.debug("Kanaler �r '" + kanaler + "'.");
      log.debug("Driftavbrottsida �r '" + sida + "'.");
      log.debug("System �r '" + system + "'.");
      log.debug("Marginal �r '" + marginal + "'.");
    }
  }

  /**
   * Avg�r om �tkomst �r till�ten eller inte.
   *
   * @return <code>true</code> om �tkomst inte �r till�ten, annars <code>false</code>
   */
  private boolean isDriftavbrott(Driftavbrott driftavbrott) {
    return driftavbrott != null;
  }
}

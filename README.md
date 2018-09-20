# mdh-driftavbrott-filter

Ett ServletFilter för webbapplikationer skrivna i Java. Gör att man kan begränsa
åtkomst till resurser i en webbapplikation baserat på information om
driftavbrott.

## Konfigurera

Så här kan en konfiguration se ut i `web.xml`:

```
  <filter>
    <description>
      Utför en kontroll av att resursen får användas vid tiden för accessen.
    </description>
    <filter-name>DriftavbrottFilter</filter-name>
    <filter-class>se.mdh.driftavbrott.filter.DriftavbrottFilter</filter-class>
    <init-param>
      <description>
        De kanaler som vi ska lyssna på. Om man vill lyssna på flera kanaler
        så ska de separeras med kommatecken.
      </description>
      <param-name>kanaler</param-name>
      <param-value>ladok.produktionssattning,ladok.uppgradering</param-value>
    </init-param>
    <init-param>
      <description>
        Den sida som ska visas om den resurs som filtret skyddar inte ska vara
        tillgänglig vid tiden för accessen.
      </description>
      <param-name>sida</param-name>
      <param-value>/WEB-INF/jsp/fel/driftavbrott.jsp</param-value>
    </init-param>
    <init-param>
      <description>
        ArtifactId för det system som är intresserat av information om
        driftavbrott.
      </description>
      <param-name>system</param-name>
      <param-value>mdh-parkering</param-value>
    </init-param>
    <init-param>
          <description>
            Marginaler i minuter för kontroll av driftavbrott. 
          </description>
          <param-name>marginal</param-name>
          <param-value>15</param-value>
        </init-param>
  </filter>
  ...
  <filter-mapping>
    <filter-name>DriftavbrottFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

Eftersom produkten använder mdh-driftavbrott-ws-client behöver den
konfigurationsfilen `se.mdh.driftavbrott.properties` som ska innehålla en URL
till mdh-driftavbrott-service. Till exempel så här:

```
se.mdh.driftavbrott.service.url=http://localhost:3301/mdh-driftavbrott/v1
```

## Skapa en JSP i din applikation

Förutom själva filterkonfigurationen behöver applikationen även en dedikerad JSP
som kan förse användarna med information om driftavbrott. Så här kan en enkel
sådan se ut som placeras i filen `/WEB-INF/jsp/fel/driftavbrott.jsp`:

```
<%@ page contentType="text/html" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<fmt:setBundle basename="se.mdh.driftavbrott.filter.Driftavbrott" scope="page"/>

<html>
  <head>
    <title><fmt:message key="sida.titel" /></title>
  </head>
  <body>
    <h1>
      <fmt:message key="sida.rubrik" />
    </h1>
  
    <c:set var="kanal" value="${requestScope.meddelande_key}" />
    <c:set var="start" value="${requestScope.start}" />
    <c:set var="slut" value="${requestScope.slut}" />
  
    <p>
      <fmt:message key="${kanal}">
        <fmt:param value="${start}" />
        <fmt:param value="${slut}" />
      </fmt:message>
    </p>
  </body>
</html>
```

I den `ResourceBundle` som följer med så finns `sida.titel` och `sida.rubrik`
definierade, samt texter för några vanliga typer av avbrott t.ex. `ladok.backup`
och `ladok.uppgradering`. Om du vill ha ett generellt meddelande för alla system
så kan du använda kanalen `default` vilken också är inkluderad.  

Om du inte är nöjd med meddelandena i bifogad `ResourceBundle` kan du skapa en
egen och referera till den med 
```
<fmt:setBundle basename="/sökväg/till/din/egen/ResourceBundle" scope="page"/>
```

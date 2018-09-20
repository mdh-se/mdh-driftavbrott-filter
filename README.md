# mdh-driftavbrott-filter

Ett ServletFilter f�r webbapplikationer skrivna i Java. G�r att man kan begr�nsa
�tkomst till resurser i en webbapplikation baserat p� information om
driftavbrott.

## Konfigurera

S� h�r kan en konfiguration se ut i `web.xml`:

```
  <filter>
    <description>
      Utf�r en kontroll av att resursen f�r anv�ndas vid tiden f�r accessen.
    </description>
    <filter-name>DriftavbrottFilter</filter-name>
    <filter-class>se.mdh.driftavbrott.filter.DriftavbrottFilter</filter-class>
    <init-param>
      <description>
        De kanaler som vi ska lyssna p�. Om man vill lyssna p� flera kanaler
        s� ska de separeras med kommatecken.
      </description>
      <param-name>kanaler</param-name>
      <param-value>ladok.produktionssattning,ladok.uppgradering</param-value>
    </init-param>
    <init-param>
      <description>
        Den sida som ska visas om den resurs som filtret skyddar inte ska vara
        tillg�nglig vid tiden f�r accessen.
      </description>
      <param-name>sida</param-name>
      <param-value>/WEB-INF/jsp/fel/driftavbrott.jsp</param-value>
    </init-param>
    <init-param>
      <description>
        ArtifactId f�r det system som �r intresserat av information om
        driftavbrott.
      </description>
      <param-name>system</param-name>
      <param-value>mdh-parkering</param-value>
    </init-param>
    <init-param>
          <description>
            Marginaler i minuter f�r kontroll av driftavbrott. 
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

Eftersom produkten anv�nder mdh-driftavbrott-ws-client beh�ver den
konfigurationsfilen `se.mdh.driftavbrott.properties` som ska inneh�lla en URL
till mdh-driftavbrott-service. Till exempel s� h�r:

```
se.mdh.driftavbrott.service.url=http://localhost:3301/mdh-driftavbrott/v1
```

## Skapa en JSP i din applikation

F�rutom sj�lva filterkonfigurationen beh�ver applikationen �ven en dedikerad JSP
som kan f�rse anv�ndarna med information om driftavbrott. S� h�r kan en enkel
s�dan se ut som placeras i filen `/WEB-INF/jsp/fel/driftavbrott.jsp`:

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

I den `ResourceBundle` som f�ljer med s� finns `sida.titel` och `sida.rubrik`
definierade, samt texter f�r n�gra vanliga typer av avbrott t.ex. `ladok.backup`
och `ladok.uppgradering`. Om du vill ha ett generellt meddelande f�r alla system
s� kan du anv�nda kanalen `default` vilken ocks� �r inkluderad.  

Om du inte �r n�jd med meddelandena i bifogad `ResourceBundle` kan du skapa en
egen och referera till den med 
```
<fmt:setBundle basename="/s�kv�g/till/din/egen/ResourceBundle" scope="page"/>
```

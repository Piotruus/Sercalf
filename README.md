# LifeSteal 2.0 — fizyczne serca (Paper 1.21.4)

## Jak dziala
- Zabicie gracza przez innego gracza powoduje utrate 1 serca zycia ofiary.
- Z ofiary wypada przedmiot SERCE. Zabojca musi go podniesc — serce nie trafia automatycznie do ekwipunku.
- Klikniecie PPM fizycznym sercem zuzywa przedmiot i dodaje 1 serce zycia (do limitu).
- `/wyplacserca <ilosc>` zamienia wlasne serca zycia na fizyczne przedmioty. Nie mozna zejsc ponizej minimum.
- `/sercailosc <gracz>` pokazuje serca zycia oraz fizyczne serca w ekwipunku.
- `/sercadaj <gracz> <ilosc>` daje fizyczne serca (OP/uprawnienie `lifesteal.admin`).
- `/sercazabierz <gracz> <ilosc>` zabiera fizyczne serca z ekwipunku gracza.
- `/sercaset <gracz> <ilosc>` ustawia serca zycia gracza.
- Liczba serc zycia zapisywana jest w `plugins/LifeSteal/players.yml`.

## Instalacja i budowanie
1. Wymagane Java 21 i Maven.
2. W katalogu z `pom.xml` uruchom `mvn package`.
3. Skopiuj `target/LifeSteal-2.0.0.jar` do folderu `plugins` serwera Paper 1.21.4.
4. Uruchom serwer.

## Wyglad serca
Domyslnie serce jest czerwonym barwnikiem z nazwa i lore, oznaczonym wewnetrznym tagiem, aby nie mozna bylo podrobic go zwyklym barwnikiem. Dokladna tekstura serca z przeslanego przykladu wymaga resource packa. `heart-item-material` mozna zmienic w config.yml.
To jest projekt zrodlowy, nie skompilowany i nieprzetestowany na dzialajacym serwerze.

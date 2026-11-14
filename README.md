# med-app-be

Backend di **Studio Medico "La Mole"**: API REST in **Java 21** e **Spring Boot 4.1** per la gestione di prenotazioni, visite, fatturazione, referti e dashboard di uno studio medico associato.

Fa parte di un progetto in tre repository:

| Repository | Contenuto |
|---|---|
| `med-app-be` (questo) | Backend, API REST |
| [med-app-fe](https://github.com/dumi05/med-app-fe) | Frontend Angular |
| [med-app-infra](https://github.com/dumi05/med-app-infra) | Docker Compose per avviare tutto insieme |

## Cosa c'è nel progetto

Stack: Java 21, Spring Boot 4.1.1, Spring Security con token JWT (jjwt), Spring Data JPA su PostgreSQL 16, springdoc-openapi (Swagger UI), OpenPDF per le fatture in PDF. I test usano H2 in memoria.

Il codice è in `src/main/java/com/dc/medapp/medappbe/`, diviso in **moduli per funzionalità**. Ogni modulo ha di norma controller, service, repository, entità e DTO.

| Modulo | Funzione |
|---|---|
| `auth` | Registrazione, login, utenti, token JWT, politica delle password, blocco dopo tentativi falliti |
| `patient` | Anagrafica dei pazienti, consenso GDPR, esportazione e anonimizzazione dei dati |
| `doctor` | Anagrafica e gestione dei medici |
| `specialization` | Specializzazioni mediche |
| `schedule` | Orari di lavoro e slot di disponibilità (anche generati in automatico) |
| `leaves` | Assenze dei medici |
| `appointments` | Prenotazione e ciclo di vita dell'appuntamento, calcolo del rischio di mancata presentazione |
| `reports` | Referti e allegati |
| `billing` | Listino prezzi, pagamento e fatture (PDF) |
| `delegations` | Deleghe tra medici e delega dei referti a un familiare tramite link e codice |
| `news` | Avvisi dei medici ai pazienti |
| `dashboard` | Indicatori per l'amministratore (incassi, tasso di presenza) |
| `audit` | Registro degli accessi e delle operazioni |
| `security`, `common`, `config` | Configurazione di sicurezza, eccezioni e entità di base, caricamento dei dati iniziali |

Il ciclo di vita di un appuntamento è: prenotato → completato → fatturato → refertato (oppure annullato).

## Come avviarlo

Il backend da solo ha bisogno di un database PostgreSQL. Per avviare l'intera applicazione (database, backend e frontend) la strada più semplice è `med-app-infra`; vedi la sezione "Lanciare tutto insieme".

### Con Docker (solo il backend)

Il `Dockerfile` compila il progetto in due fasi e produce un'immagine con solo il runtime Java. Serve comunque un database raggiungibile.

```bash
docker build -t med-app-be .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/studiomedico-dc \
  -e DB_USERNAME=studiomedico -e DB_PASSWORD=studiomedico \
  -e JWT_SECRET=una-chiave-lunga-almeno-32-caratteri \
  med-app-be
```

`host.docker.internal` funziona con Docker Desktop; l'esempio presume un database PostgreSQL su `localhost:5433` (vedi sotto).

### In locale, senza Docker per il backend

Prerequisiti: **JDK 21** e un PostgreSQL. Un database con le impostazioni predefinite del progetto si avvia così:

```bash
docker run --name med-db -e POSTGRES_USER=studiomedico -e POSTGRES_PASSWORD=studiomedico \
  -e POSTGRES_DB=studiomedico-dc -p 5433:5432 -d postgres:16
```

Poi, dalla cartella del progetto:

```bash
./mvnw spring-boot:run
```

(su Windows `mvnw.cmd spring-boot:run`). L'API risponde su **http://localhost:8080** e la documentazione interattiva su **http://localhost:8080/swagger-ui.html**. Le tabelle vengono create automaticamente al primo avvio.

All'avvio vengono creati, se mancano, le specializzazioni di base, il listino e l'account amministratore `admin@studio-medico.it` con la password `ADMIN_PSW` (predefinita `admin123`, da cambiare al primo accesso).

## Configurazione

Le impostazioni sono in `src/main/resources/application.yml` e si cambiano con variabili d'ambiente. Le principali:

| Variabile | Valore predefinito | Significato |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/studiomedico-dc` | Indirizzo del database |
| `DB_USERNAME`, `DB_PASSWORD` | `studiomedico` | Credenziali del database |
| `JWT_SECRET` | valore di sviluppo | Chiave di firma dei token, almeno 32 caratteri |
| `JWT_EXPIRATION_MS` | `86400000` (24 ore) | Durata del token |
| `ADMIN_PSW` | `admin123` | Password iniziale dell'amministratore |
| `STORAGE_DIR` | `./data/attachments` | Cartella degli allegati dei referti |
| `DEMO_DATA`, `DEMO_PASSWORD` | `false`, vuota | Dati di prova (solo per dimostrazioni) |
| `SERVER_PORT` | `8080` | Porta del server |

I valori predefiniti servono solo allo sviluppo locale: in ogni altro ambiente vanno sostituiti, in particolare `JWT_SECRET` e le password.

Con il frontend in sviluppo (`ng serve`, porta 4200) le richieste cross-origin sono consentite solo da `http://localhost:4200`.

## Test

```bash
./mvnw test       # esegue gli unit test e i test di integrazione
./mvnw verify     # come la CI: compila, esegue i test e verifica
```

I test usano il profilo `test` con un database H2 in memoria, quindi non richiedono PostgreSQL. Su ogni push e pull request verso `main`, GitHub Actions esegue `mvn verify` e verifica che l'immagine Docker si costruisca.

## Lanciare tutto insieme: serve per forza `med-app-infra`?

No, ma è il modo più semplice. Le alternative:

- **`med-app-infra` (consigliato)**: un solo `docker compose up --build` avvia database, backend e frontend, già collegati fra loro. Vedi il README di quel repository.
- **Tutto in locale**: database (comando `docker run` sopra), backend con `./mvnw spring-boot:run` e frontend con `npm start`. Il frontend inoltra le chiamate a `/api` a `localhost:8080` tramite il proxy di sviluppo.
- **Container avviati a mano**: possibile, ma richiede una rete Docker comune e un container del backend chiamato `backend`, nome che il frontend si aspetta. È più laborioso e non è documentato qui.

## Note

Il backend non configura HTTPS: per un uso reale con dati sanitari serve un certificato o un reverse proxy.

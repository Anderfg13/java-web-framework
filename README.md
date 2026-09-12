# Mini Web Application — Sequential HTTP Server

## Descripción del proyecto

Este proyecto extiende un servidor HTTP mínimo, construido directamente sobre sockets de Java (sin frameworks web), hasta convertirlo en una pequeña aplicación web que sirve recursos estáticos (HTML, JavaScript, imágenes) y expondrá un conjunto reducido de servicios dinámicos hardcodeados. El objetivo pedagógico es entender, "a mano", cómo funciona el protocolo HTTP y dónde están los límites reales de un servidor de una sola conexión, antes de introducir concurrencia o distribución.

**Alcance actual:** el servidor es intencionalmente secuencial (procesa una conexión completa antes de aceptar la siguiente), corre en una sola instancia, y no usa hilos, pools, balanceadores, contenedores, bases de datos ni autenticación.

## Estado actual del proyecto

Este README documenta el proyecto **en construcción**, siguiendo la evolución descrita en la guía del laboratorio. Estado por etapa:

| Etapa | Descripción | Estado |
|---|---|---|
| 0 | Configuración de Maven (JUnit 5, JDK objetivo) | ✅ Hecho |
| 1 | Servidor de una sola conexión (verificación del protocolo) | ✅ Hecho |
| 2 | Bucle secuencial de múltiples conexiones | ✅ Hecho |
| 3 | Recursos estáticos: content-type, bytes, 404, 405, protección path traversal | ✅ Hecho |
| 4 | Servicios hardcodeados (greeting, square, server-time, health) | ⏳ Pendiente |
| 5 | Cliente asíncrono en JavaScript (fetch, estados de carga/error) | ⏳ Pendiente |
| 6 | Pruebas unitarias con JUnit 5 | ⏳ Pendiente |
| 7 | Empaquetado y despliegue en AWS EC2 | ⏳ Pendiente |

## Metáfora del sistema

Piensa en el servidor como **un mesero que atiende un restaurante pequeño, uno a la vez**:

- **La puerta del restaurante** (`ServerSocket`) siempre está abierta, esperando que llegue un cliente nuevo.
- **El mesero** (el bucle principal del servidor) atiende a **una sola mesa a la vez**: recibe el pedido completo, lo prepara, lo entrega, y solo entonces va a atender a la siguiente mesa. Nunca atiende dos mesas al mismo tiempo — esa es la limitación intencional de esta etapa del proyecto.
- **La vitrina de platos fijos** (la carpeta `public/`) contiene los recursos que el mesero simplemente saca y sirve tal cual: la página HTML, el script JavaScript, y las imágenes.
- **Los platos que se cocinan al momento** son los servicios hardcodeados (greeting, square, server-time, health): el mesero reconoce un pedido exacto y genera una respuesta dinámica en JSON, sin improvisar ni delegar en un sistema de "menú genérico" (no hay un framework de ruteo).
- **El cliente que hace pedidos rápidos sin bloquear la conversación** es el navegador: usando JavaScript asíncrono, el cliente puede pedir varias cosas "en segundo plano" sin congelar la página — pero eso no cambia que, del otro lado, el mesero sigue atendiendo un pedido a la vez.
- **Mudar el restaurante a otro local** es desplegar la misma aplicación en una instancia EC2: cambia la dirección y quién puede tocar la puerta (el grupo de seguridad de AWS), pero el mesero sigue siendo uno solo.

### Diagrama de arquitectura

```
                     ┌─────────────────────────┐
                     │        Navegador         │
                     │  (HTML + JS asíncrono)   │
                     └────────────┬─────────────┘
                                  │ HTTP (peticiones GET)
                                  ▼
                     ┌─────────────────────────┐
                     │   ServerSocket (puerto)  │  <- "la puerta", siempre abierta
                     └────────────┬─────────────┘
                                  │ accept() por conexión
                                  ▼
                     ┌─────────────────────────┐
                     │  Bucle secuencial        │  <- "el mesero": un cliente a la vez
                     │  (while true + try/catch)│
                     └──────┬───────────┬───────┘
                             │           │
                 recurso     │           │  ruta de servicio
                 estático    ▼           ▼  (pendiente, Etapa 4)
                ┌─────────────────┐  ┌─────────────────────┐
                │  Carpeta public/│  │ Servicios hardcodeados│
                │  (HTML, JS, img)│  │ (greeting, square,    │
                └─────────────────┘  │  server-time, health) │
                                     └─────────────────────┘
```

### Responsabilidad de cada componente

- **Navegador / cliente JS**: construye las peticiones a partir de la interacción del usuario, las envía de forma asíncrona, y actualiza solo la parte de la página que corresponde (sin recargar). *(Aún no implementado — Etapa 5.)*
- **`ServerSocket`**: escucha en un puerto fijo y acepta conexiones entrantes, una por una.
- **Bucle principal (`main`)**: por cada conexión aceptada, lee la primera línea de la petición (request line), decide si es una petición válida (método GET, no un intento de path traversal), y construye una respuesta completa antes de cerrar esa conexión y volver a esperar la siguiente. Un error en una conexión (`try/catch` genérico) nunca tumba el servidor completo.
- **Carpeta `public/`**: contiene los recursos estáticos servidos tal cual, leídos siempre como bytes (para que el mismo camino sirva tanto texto como binarios).
- **Servicios hardcodeados**: rutas especiales reconocidas con condiciones explícitas (no un framework de ruteo), que generan respuestas JSON dinámicas. *(Aún no implementado — Etapa 4.)*

## Decisiones de diseño

- **¿Por qué el servidor es secuencial?** Es un requisito explícito del laboratorio: entender primero el comportamiento y los límites de un servidor de una sola conexión, antes de introducir concurrencia. Esto se implementa con un `while(true)` que solo vuelve a `accept()` después de cerrar por completo la conexión anterior.
- **¿Por qué las rutas son hardcodeadas?** El objetivo es que el mecanismo de "un path selecciona un comportamiento" quede explícito y legible con `if`/`switch`, sin esconderlo detrás de un framework, anotaciones o reflexión.
- **¿Cómo se seleccionan los content-types?** Mediante un mapeo fijo por extensión de archivo (`.html`, `.js`, `.png`, `.jpg`/`.jpeg`), con un valor por defecto (`application/octet-stream`) para extensiones desconocidas.
- **¿Cómo se rechazan rutas inseguras?** El path solicitado se resuelve contra la carpeta base `public/`, se normaliza con `Path.normalize()` (para colapsar cualquier `..`), y se verifica con `Path.startsWith(...)` que el resultado siga estando dentro de la carpeta base. Si no, se responde `403 Forbidden` sin revelar más información.
- **¿Por qué el cliente será asíncrono?** Para que la interfaz no se congele mientras espera una respuesta del servidor — pero esto es una propiedad del cliente, no del servidor: el servidor sigue atendiendo una conexión a la vez sin importar cuántas peticiones asíncronas mande el navegador.

## Estructura del proyecto

```
networking-lab-2/
├── pom.xml                          # Descriptor de Maven (dependencias, build)
├── public/                          # Recursos públicos servidos por el servidor
│   ├── index.html
│   ├── script.js
│   └── images/
│       ├── Databricks_Logo.png
│       └── File-Handling-in-Java.jpg
└── src/
    ├── main/java/co/edu/escuelaing/webapplication/webapplication/
    │   └── Webapplication.java      # Servidor HTTP (punto de entrada)
    └── test/java/                   # Pruebas unitarias (pendiente, Etapa 6)
```

La carpeta `public/` vive **al lado** del código fuente (no dentro de `src/main/resources`) a propósito: así el servidor la lee como archivos reales del sistema de archivos (necesario para la validación de path traversal), y puede acompañar al artefacto empaquetado como una carpeta independiente al momento del despliegue.

## Prerrequisitos

- **Java 21** (JDK). Verifica con `java -version`.
- **Maven 3.9+**. Verifica con `mvn -version`.
- Un navegador moderno para las pruebas manuales.

## Instalación y build

```bash
git clone https://github.com/Anderfg13/networking-lab-2.git
cd networking-lab-2
mvn clean compile
```

## Cómo correrlo localmente

El puerto está actualmente fijo en el código (`35000`) — se hará configurable en una etapa posterior. Para ejecutar:

- **Desde NetBeans**: botón "Run" sobre el proyecto (usa el goal `exec:exec` configurado internamente).
- **Desde terminal**, tras compilar:
  ```bash
  mvn exec:exec
  ```

Luego abre `http://localhost:35000/` en el navegador. Para detenerlo, interrumpe el proceso (Ctrl+C en la terminal, o el botón de stop en NetBeans).

## Cómo usar la aplicación (estado actual)

Por ahora la aplicación sirve recursos estáticos:

- `http://localhost:35000/` → sirve `index.html`.
- `http://localhost:35000/script.js`, `.../images/*.png`, `.../images/*.jpg` → sirven los recursos correspondientes con su content-type correcto.
- Una ruta inexistente responde `404 Not Found`.
- Un método distinto de `GET` (probado con Postman/curl) responde `405 Method Not Allowed`.
- Un intento de salir de la carpeta `public/` (ej. `/../pom.xml`, probado con `curl --path-as-is`) responde `403 Forbidden`.

Los servicios dinámicos (`greeting`, `square`, `server-time`, `health`) y la interfaz interactiva en JavaScript están pendientes (Etapas 4 y 5).

## Cómo correr las pruebas

Las pruebas automatizadas con JUnit 5 están pendientes (Etapa 6). La dependencia ya está configurada en `pom.xml` con `scope=test`, lista para agregar clases bajo `src/test/java`. Mientras tanto, la validación es manual:

- Verificación del protocolo con las herramientas de desarrollador del navegador (pestaña Network).
- Verificación de métodos no soportados y path traversal con Postman / `curl`.

## Despliegue en AWS (pendiente)

El despliegue a una instancia EC2 (Etapa 7) todavía no se ha realizado. Se documentará aquí una vez completado: transferencia del artefacto empaquetado junto con la carpeta `public/`, instalación del JDK 21 en la instancia, arranque como servicio administrado, y verificación del servicio de salud desde la instancia antes de probarlo externamente.

## Evidencia y resultados

Pendiente de adjuntar capturas de: carga de recursos estáticos con status y content-type correctos, respuesta 404/405/403, y ejecución remota en EC2.

## Limitaciones conocidas

- El servidor es **estrictamente secuencial**: atiende una conexión completa antes de aceptar la siguiente. No hay hilos, pools ni concurrencia de ningún tipo.
- Solo soporta el método `GET`; cualquier otro método recibe `405`.
- Las rutas de servicio son **hardcodeadas**, no hay un framework de ruteo ni descubrimiento dinámico de endpoints.
- No es un servidor HTTP de producción: no implementa keep-alive, HTTPS, compresión, ni manejo completo de todos los headers del estándar HTTP.
- Corre en una única instancia EC2: un solo punto de falla y una sola capacidad de cómputo.

## Autor y agradecimientos

**Autor:** Anderson Fabian Garcia Nieto.

Este proyecto se desarrolló como parte del laboratorio "From a Minimal HTTP Server to a Web Application on AWS" del curso de Telemática/TDSE. El desarrollo contó con la asistencia de Claude (Anthropic) como tutor conceptual durante la implementación.

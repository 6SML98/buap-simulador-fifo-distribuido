# Simulador FIFO distribuido

Simulador de planificación FIFO con servidor XML-RPC, clientes de consola y Swing, monitor y gráfica de Gantt.

## Requisitos

JDK 17 y cinco JAR en lib/: xmlrpc-server/client/common 3.1.3, ws-commons-util 1.0.2 y commons-logging 1.2.

## Ejecutar

Abre servidor/ y cliente/ como proyectos NetBeans separados. Sus referencias apuntan a la carpeta lib/ compartida.

Para compilar desde PowerShell:

```powershell
New-Item -ItemType Directory build/classes -Force | Out-Null
$fuentes = (Get-ChildItem servidor/src,cliente/src -Recurse -Filter *.java).FullName
javac -encoding UTF-8 -cp "lib/*" -d build/classes $fuentes
java -cp "build/classes;lib/*" rpcfifo.fifoServer 8080
```

En otra terminal: `java -cp "build/classes;lib/*" DynamicClient http://localhost:8080/`. También existe rpcfifo.ClienteSwing.

## Verificación del 8 de octubre de 2026

Servidor y clientes compilados; llamadas RPC de reinicio, login, envío, finalización y resumen comprobadas. El cliente de consola se ejecutó. Se corrigieron las rutas de las bibliotecas y el destino Java 11, incompatible con la sintaxis del cliente. Las ventanas Swing no se recorrieron visualmente.

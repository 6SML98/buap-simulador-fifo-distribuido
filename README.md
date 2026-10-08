# Simulador FIFO distribuido

Proyecto final cliente-servidor Java: simulación FIFO de procesos, monitor y gráfica de Gantt.

Proyecto académico de BUAP. Proyecto final seleccionado.

## Organización

- `servidor/`: servidor FIFO, monitor y proyecto NetBeans.
- `cliente/`: cliente de consola y Swing con proyecto NetBeans.

Abre cada carpeta como un proyecto independiente. Inicia primero el servidor y ajusta la dirección de conexión del cliente para tu entorno.

## Documentación y requisitos



## Tecnologías y archivos

Extensiones de código: .java, .xml.

## Ejecución

Abrir `servidor/` y `cliente/` por separado con Apache NetBeans. Revisar las dependencias en el archivo `nbproject/project.properties` de cada proyecto y ajustar las rutas locales de bibliotecas si corresponde.

Clases con método `main`:

- `rpcfifo.fifoServer`
- `rpcfifo.MonitorPuro`

Las clases RPC requieren Apache XML-RPC 3.1.3 y sus dependencias. Las bibliotecas originales se conservan en la carpeta de la materia; configurar su instalación en el IDE antes de compilar.

## Contenido publicado

Se conserva el código y los recursos referenciados. Se excluyen dependencias instaladas, resultados de compilación, configuraciones personales, documentos ajenos al programa y datos locales.

## Estado

Archivo académico original. Puede contener operaciones pendientes o dependencias antiguas. No se ha verificado la ejecución de todos los programas.

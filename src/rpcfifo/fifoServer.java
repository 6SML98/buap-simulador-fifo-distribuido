package rpcfifo;

import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;

// ¡Asegúrate de importar estas 3 clases!
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class fifoServer {
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;

        System.out.println("Iniciando servidor FIFO en el puerto " + port);

        WebServer webServer = new WebServer(port);
        XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();

        PropertyHandlerMapping phm = new PropertyHandlerMapping();
        phm.addHandler("Scheduler", fifo.class); // nombre del servicio
        xmlRpcServer.setHandlerMapping(phm);

        XmlRpcServerConfigImpl cfg = (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
        cfg.setEnabledForExtensions(true);
        cfg.setContentLengthOptional(false);

        // 1. Inicia el reloj
        fifo.startSimulationTicker();
        final fifo fifoInstance = new fifo();

        // 2.Inicia el monitor
        // Este es un SEGUNDO hilo que se ejecuta cada segundo.
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            try {
                // Llama a los métodos que acabamos de crear:
                clearConsole();
                String monitorData = fifoInstance.getMonitorServidor(); // Usa la instancia
                System.out.println(monitorData); // Imprime el monitor en la consola
                
            } catch (Exception e) {
                System.out.println("Error al actualizar monitor: " + e.getMessage());
            }
        }, 0, 5000, TimeUnit.MILLISECONDS); 

        webServer.start();
        
    }

    private static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }
}
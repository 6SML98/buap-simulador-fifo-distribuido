import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import java.net.URL;
import java.util.*;

public class DynamicClient {

    private static XmlRpcClient client;
    private static final Scanner scanner = new Scanner(System.in);
    private static final Random random = new Random();

    // Procesos enviados por ESTE cliente (para consultas)
    private static final List<String> misProcesOS = new ArrayList<>();
    
    // Sincronizar con el TICK_MS del servidor 
    private static final int TICK_MS = 5000; 

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String serverUrl = (args.length > 0) ? args[0] : "http://localhost:8080/";

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(serverUrl));
        config.setEnabledForExtensions(true);
        client = new XmlRpcClient();
        client.setConfig(config);

        System.out.println("Cliente conectado a " + serverUrl);
        System.out.println("Bienvenido al Planificador FIFO (Proyecto 1)");

      

        while (true) {
            imprimirMenu(); 
            int opcion;
            try {
                opcion = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Opción invalida.");
                continue;
            }


            switch (opcion) {
                case 1 -> enviarProceso();
                case 2 -> {
                    System.out.println("Cerrando cliente. El servidor seguira ejecutandose.");
                    return;
                }
                default -> System.out.println("Opción no valida.");
            }
            System.out.println("----------------------------------------------------------");
        }
    }

private static void imprimirMenu() {
    System.out.println();
    System.out.println("Que deseas hacer?");
    System.out.println("  1) Enviar y Monitorear Proceso (C, t)");
    System.out.println("  2) Salir");
    System.out.print("Elige una opcion: ");
}

    @SuppressWarnings("unchecked")
    private static void enviarProceso() {
        int C; // Instancia de Llegada
        int t; // Duración en Instancias

        // 1. Pedir datos al usuario (pide instancias)
        try {
            System.out.print("Ingresa la INSTANCIA de llegada (C) (ej. 1, 2, 3...): ");
            String inputC = scanner.nextLine().trim();
            C = Integer.parseInt(inputC);

            System.out.print("Ingresa la DURACIÓN en instancias (t) (ej. 1, 2...) o 'r' para aleatorio (1-4): ");
            String inputT = scanner.nextLine().trim();

            if (inputT.equalsIgnoreCase("r")) {
                t = random.nextInt(4) + 1; // 1..4 instancias
                System.out.println(">> [CLIENTE] Duracion aleatoria: " + t + " instancias");
            } else {
                t = Integer.parseInt(inputT);
                if (t < 1) t = 1;
            }

        } catch (NumberFormatException e) {
            System.out.println(">> [CLIENTE] Entrada invalida. Deben ser numeros enteros.");
            return;
        }

        // 2. Intentar enviar el proceso
        try {
            // Llama al mtodo estricto 'submitProcess' (envía instancias)
            Map<String, Object> response = 
                (Map<String, Object>) client.execute("Scheduler.submitProcess", new Object[]{C, t});
            
            String status = (String) response.get("status");
            String nombreProceso = null; 

            if ("OK".equals(status)) {
                //ASO DE eXIT (C es futuro) 
                nombreProceso = (String) response.get("name");
                System.out.println(">> [CLIENTE] Proceso enviado para Instancia C=" + C + ". Nombre asignado: " + nombreProceso);
                
            } else {
                //SO DE ERROR
                String reason = (String) response.get("reason");
                
                if ("PAST_SCHEDULE".equals(reason)) {
                  
                    int serverNow = (Integer) response.get("now");
                    System.out.println("\n----------------------------------------------------------");
                    System.out.println(">> [SERVIDOR] Tu proceso no pudo agendarse para Instancia C=" + C + ".");
                    System.out.println(">> [SERVIDOR] Razón: La instancia ya pasó (Reloj del servidor: AHORA=" + serverNow + ").");
                    System.out.println("\n¿Qué deseas hacer?");
                    System.out.println("  1) Re-enviar para la próxima instancia disponible (poner en cola AHORA).");
                    System.out.println("  2) Cancelar el proceso.");
                    System.out.print("Elige una opcion: ");

                    String input = scanner.nextLine().trim(); // Espera respuesta del usuario
                    if ("1".equals(input)) {
                        // El usuario eligiore-encolar
                        System.out.println("\n>> [CLIENTE] Re-enviando proceso (t=" + t + " instancias) para la cola de AHORA...");
                        String nombreNuevo = (String) client.execute("Scheduler.submitProcessNow", new Object[]{t, C});
                        
                        if (nombreNuevo != null) {
                            nombreProceso = nombreNuevo; // Guardamos el nuevo nombre
                            System.out.println(">> [CLIENTE] Proceso re-enviado. Nombre asignado: " + nombreProceso);
                        } else {
                            System.out.println(">> [CLIENTE] Error al re-enviar.");
                        }
                    } else {
                        // El usuario eligio cancelar
                        System.out.println("\n>> [CLIENTE] Proceso cancelado.");
                    }
                    
                } else if ("INVALID_T".equals(reason)) {
                    // Otro error
                    System.out.println(">> [CLIENTE] El servidor rechazo el proceso: La duracion (t) debe ser > 0.");
                } else {
                    // Otro error
                    System.out.println(">> [CLIENTE] El servidor rechazo el proceso: " + response.get("message"));
                }
            }

            // 3. Monitorear SI Y SOLO SI tenemos un nombre (o sea, si no se canceló)
            if (nombreProceso != null) {
                misProcesOS.add(nombreProceso); // Añade el proceso a la lista
                monitorearProcesoEnVivo(nombreProceso); // Llama al monito
            } else {
                // el proceso se cancelo o fallo
                System.out.println(">> [CLIENTE] El proceso no fue enviado. Volviendo al menu.");
                Thread.sleep(1500);
            }

        } catch (Exception e) {
            System.err.println("Error grave al contactar el servidor: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void verTablasDinamicas() {
        System.out.println(">> [CLIENTE] Solicitando Tablas Dinamicas...");
        try {
            Map<String, Object> state =
                    (Map<String, Object>) client.execute("Scheduler.getDynamicTables", Collections.emptyList());

            System.out.println("\n========== TABLAS DINAMICAS (AHORA: " + state.get("now") + ") ==========");

            // CPU
            System.out.println("\n--- 1. PROCESO EN CPU ---");
            Map<String, Object> running = (Map<String, Object>) state.get("running");
            if (running == null) {
                System.out.println("[INACTIVO] Procesador vacio.");
            } else {
                System.out.println("Nombre\tC\t t\tinicio");
                System.out.printf("%s\t%d\t%d\t%d%n",
                        running.get("name"), running.get("C"), running.get("t"), running.get("start"));
            }

            // LISTOS
            System.out.println("\n--- 2. COLA DE LISTOS ---");
            Object[] ready = (Object[]) state.get("ready");
            if (ready.length == 0) {
                System.out.println("[VACiA]");
            } else {
                System.out.println("Pos\tNombre\tC\t t");
                int pos = 1;
                for (Object o : ready) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    System.out.printf("#%d\t%s\t%d\t%d%n", pos++, p.get("name"), p.get("C"), p.get("t"));
                }
            }

            // FUTUROS
            System.out.println("\n--- 3. COLA DE FUTUROS (por llegar) ---");
            Object[] future = (Object[]) state.get("future");
            if (future.length == 0) {
                System.out.println("[VACIA]");
            } else {
                System.out.println("Nombre\tC\t t");
                for (Object o : future) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    System.out.printf("%s\t%d\t%d%n", p.get("name"), p.get("C"), p.get("t"));
                }
            }
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("Error al obtener tablas dinamicas: " + e.getMessage());
        }
    }

    /** 3) Resumen de procesos terminados (E, F, P) */
    @SuppressWarnings("unchecked")
    private static void verResumenTerminados() {
        System.out.println(">> [CLIENTE] Solicitando resumen...");
        try {
            Map<String, Object> summary =
                    (Map<String, Object>) client.execute("Scheduler.getSummary", Collections.emptyList());
            Object[] rows = (Object[]) summary.get("rows");

            System.out.println("\n========== RESUMEN DE PROCESOS TERMINADOS ==========");
            if (rows.length == 0) {
                System.out.println("Aun no hay procesos terminados.");
                return;
            }

            System.out.println("Nombre\tInicio\tFin\tE\t t\tF\t P");
            System.out.println("--------------------------------------------------------");
            for (Object rowObj : rows) {
                Map<String, Object> r = (Map<String, Object>) rowObj;
                System.out.printf("%s\t%d\t%d\t%d\t%d\t%d\t%.2f%n",
                        r.get("name"), r.get("start"), r.get("finish"), r.get("E"),
                        r.get("t"), r.get("F"), r.get("P"));
            }
            System.out.println("--------------------------------------------------------");
            System.out.printf("PROMEDIOS: E=%.2f  F=%.2f  P=%.2f%n",
                    (Double) summary.get("avgE"), (Double) summary.get("avgF"), (Double) summary.get("avgP"));
            System.out.println("======================================================");
        } catch (Exception e) {
            System.err.println("Error al obtener resumen: " + e.getMessage());
        }
    }

    /** 4) Consulta manual del estado de un proceso */
    @SuppressWarnings("unchecked")
    private static void consultarProceso() {
        System.out.println("Procesos enviados por este cliente: " + misProcesOS);
        System.out.print("Ingresa el nombre a consultar (ej. P-1): ");
        String nombre = scanner.nextLine().trim();
        if (nombre.isEmpty()) {
            System.out.println("Nombre invalido.");
            return;
        }

        try {
            Map<String, Object> st =
                    (Map<String, Object>) client.execute("Scheduler.getProcessStatus", new Object[]{nombre});
            String status = (String) st.get("status");
            String detalle = (String) st.get("details");
            String etiqueta = switch (status) {
                case "RUNNING" -> "EJECUTANDO";
                case "READY" -> "LISTO";
                case "FUTURE" -> "FUTURO";
                case "COMPLETED" -> "TERMINADO";
                case "REJECTED" -> "RECHAZADO";
                case "NOT_FOUND" -> "NO_ENCONTRADO";
                default -> status;
            };

            System.out.println("\n--- RESULTADO ---");
            System.out.println("Proceso: " + nombre);
            System.out.println("Estado : " + etiqueta);
            System.out.println("Detalle: " + detalle);
            System.out.println("-----------------");
        } catch (Exception e) {
            System.err.println("Error al consultar proceso: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void verGraficaASCII(boolean soloMisProcesos) {
        System.out.println(">> [CLIENTE] Iniciando monitor grafico ASCII...");
        System.out.println(">> (Presiona [Enter] para detener el monitor y volver al menu)...");
        try {
            Thread.sleep(2000); // Pausa para que el usuario lea
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Thread inputThread = new Thread(() -> {
            try {
                scanner.nextLine();
            } catch (Exception e) { /* Hilo interrumpido */ }
        });
        inputThread.start();

        try {
            while (inputThread.isAlive()) {
                Map<String, Object> state = 
                    (Map<String, Object>) client.execute("Scheduler.getDynamicTables", Collections.emptyList());
                Map<String, Object> summary = 
                    (Map<String, Object>) client.execute("Scheduler.getSummary", Collections.emptyList());

                int ahora = (Integer) state.get("now");
                
                clearConsole();

                String titulo = soloMisProcesos ? "MI MONITOR DE PROCESOS" : "MONITOR GLOBAL DEL SERVIDOR";
                System.out.println("=========================================================================");
                System.out.println("== " + titulo + " (Presiona [Enter] para salir)");
                System.out.println("=========================================================================");
                System.out.println("AHORA: " + ahora + "\n");
                
                System.out.println("Proceso |0...5...10..15..20..25..30..35..40..45..50..55..60..65..70..75..80");
                System.out.println("--------|----+----|----+----|----+----|----+----|----+----|----+----|----|");

                Object[] rows = (Object[]) summary.get("rows");
                for (Object rowObj : rows) {
                    Map<String, Object> r = (Map<String, Object>) rowObj;
                    String name = (String) r.get("name");
                    
                    if (soloMisProcesos && !misProcesOS.contains(name)) {
                        continue;
                    }
                    
                    int start = (Integer) r.get("start");
                    int t = (Integer) r.get("t");
                    
                    String barraEspera = repetirCaracter('.', Math.max(0, start)); 
                    String barraEjec = repetirCaracter('=', t);
                    
                    System.out.printf("%-7s |%s%s (Fin: %d)%n", name, barraEspera, barraEjec, r.get("finish"));
                }

                Map<String, Object> running = (Map<String, Object>) state.get("running");
                if (running != null) {
                    String name = (String) running.get("name");
                    
                    if (!soloMisProcesos || misProcesOS.contains(name)) {
                        int start = (Integer) running.get("start");
                        int t = (Integer) running.get("t");
                        int transcurrido = Math.max(0, ahora - start);
                        int restante = Math.max(0, t - transcurrido);
                        
                        String barraEspera = repetirCaracter('.', Math.max(0, start));
                        String barraEjec = repetirCaracter('=', transcurrido);
                        String barraFutura = repetirCaracter('#', restante); 
                        
                        System.out.printf("%-7s |%s%s%s (Corriendo)%n", name, barraEspera, barraEjec, barraFutura);
                    }
                }

                System.out.println("\n--- Colas del Servidor ---");
                
                Object[] ready = (Object[]) state.get("ready");
                System.out.print("LISTOS:   ");
                if (ready.length == 0) System.out.print("[VACIA]");
                else {
                    for (Object o : ready) {
                        Map<String, Object> p = (Map<String, Object>) o;
                        boolean esMio = misProcesOS.contains((String)p.get("name"));
                        if(soloMisProcesos && !esMio) continue;
                        
                        if(esMio) System.out.print("*" + p.get("name") + "*(t=" + p.get("t") + ") ");
                        else System.out.print(p.get("name") + "(t=" + p.get("t") + ") ");
                    }
                }
                System.out.println();

                Object[] future = (Object[]) state.get("future");
                System.out.print("FUTURO:   ");
                if (future.length == 0) System.out.print("[VACIA]");
                else {
                    for (Object o : future) {
                        Map<String, Object> p = (Map<String, Object>) o;
                        boolean esMio = misProcesOS.contains((String)p.get("name"));
                        if(soloMisProcesos && !esMio) continue; 

                        if(esMio) System.out.print("*" + p.get("name") + "*(C=" + p.get("C") + ") ");
                        else System.out.print(p.get("name") + "(C=" + p.get("C") + ") ");
                    }
                }
                System.out.println("\n\n(Presiona [Enter] para salir...)");
                
                Thread.sleep(TICK_MS);

            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); 
            System.out.println(">> [CLIENTE] Monitor interrumpido.");
        } catch (Exception e) {
            System.out.println(">> [CLIENTE] Error de conexion con el servidor: " + e.getMessage());
        }
        
        if (inputThread.isAlive()) {
            inputThread.interrupt();
        }
        System.out.println(">> [CLIENTE] Volviendo al menu principal...");
        try { Thread.sleep(500); } catch (InterruptedException e) {}
    }


    /** Intenta "limpiar" la consola. */
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

    /** Repite un carácter N veces. */
    private static String repetirCaracter(char c, int n) {
        if (n <= 0) return "";
        char[] buf = new char[n];
        Arrays.fill(buf, c);
        return new String(buf);
    }
    @SuppressWarnings("unchecked")
    private static void monitorearProcesoEnVivo(String nombreProceso) {
        System.out.println(">> [CLIENTE] Iniciando monitor en vivo para " + nombreProceso + "...");
        System.out.println(">> El monitor se detendrá automáticamente cuando el proceso termine.");
        try {
            Thread.sleep(2000); // Pausa para que el usuario lea
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String estadoActual = "INICIANDO";
        String detalleActual = "";

        try {
            // Bucle principal: se ejecuta hasta que el proceso termine
            while (true) {
                
                // 1. Obtener datos FRESCOS del servidor
                Map<String, Object> state = 
                    (Map<String, Object>) client.execute("Scheduler.getDynamicTables", Collections.emptyList());
                Map<String, Object> summary = 
                    (Map<String, Object>) client.execute("Scheduler.getSummary", Collections.emptyList());
                Map<String, Object> statusProc = 
                    (Map<String, Object>) client.execute("Scheduler.getProcessStatus", new Object[]{nombreProceso});

                int ahora = (Integer) state.get("now");
                
                // 2. Limpiar la pantalla
                clearConsole();

                // 3. Imprimir el encabezado (actualizado)
                System.out.println("================== MI MONITOR DE PROCESOS ==================");
                System.out.println("== Rastreando: " + nombreProceso + " (Monitor se cierra al finalizar)");
                System.out.println("============================================================");
                System.out.println("INSTANCIA (AHORA): " + ahora + " (Tick cada 5s)\n"); // Texto actualizado
                
                // 4. Dibujar el eje de tiempo
                System.out.println("Proceso |0..1..2..3..4..5..6..7..8..9..10.11.12.13.14.15.16");
                System.out.println("--------|--|--|--|--|--|--|--|--|--|--|--|--|--|--|--|--|\n");

                // 5. Dibujar Procesos TERMINADOS (filtrado)
                Object[] rows = (Object[]) summary.get("rows");
                for (Object rowObj : rows) {
                    Map<String, Object> r = (Map<String, Object>) rowObj;
                    String name = (String) r.get("name");
                    
                    if (misProcesOS.contains(name)) { // Filtro

                        // Obtenemos los valores del Map PRIMERO
                        int start = (Integer) r.get("start");
                        int t = (Integer) r.get("t");
                        int finish = (Integer) r.get("finish");

                        String barraEspera = repetirCaracter('.', start * 3); 
                        String barraEjec = repetirCaracter('#', t * 3);
                        System.out.printf("%-7s |%s%s (Fin: %d)%n", name, barraEspera, barraEjec, finish);

                    }
                }

                // 6. Dibujar Proceso EN_CPU (filtrado)
                Map<String, Object> running = (Map<String, Object>) state.get("running");
                if (running != null) {
                    String name = (String) running.get("name");
                    if (misProcesOS.contains(name)) { // Filtro
                        int start = (Integer) running.get("start");
                        int t = (Integer) running.get("t");
                        int transcurrido = Math.max(0, ahora - start);
                        int restante = Math.max(0, t - transcurrido);
                        
    
                        String barraEspera = repetirCaracter('.', start * 3);
                        String barraEjec = repetirCaracter('#', transcurrido * 3);
                        String barraFutura = repetirCaracter('=', restante * 3);
                        System.out.printf("%-7s |%s%s%s (Corriendo)%n", name, barraEspera, barraEjec, barraFutura);
                    }
                }

                // 7. Mostrar Colas 
                System.out.println("\n--- Mis Procesos en Colas ---");
                
                Object[] ready = (Object[]) state.get("ready");
                System.out.print("LISTOS:   ");
                boolean listosVacio = true;
                for (Object o : ready) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    if (misProcesOS.contains((String)p.get("name"))) {
                        System.out.print("*" + p.get("name") + "*(t=" + p.get("t") + ") ");
                        listosVacio = false;
                    }
                }
                if (listosVacio) System.out.print("[VACIA]");
                System.out.println();

                Object[] future = (Object[]) state.get("future");
                System.out.print("FUTURO:   ");
                boolean futuroVacio = true;
                for (Object o : future) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    if (misProcesOS.contains((String)p.get("name"))) {
                        System.out.print("*" + p.get("name") + "*(C=" + p.get("C") + ") ");
                        futuroVacio = false;
                    }
                }
                if (futuroVacio) System.out.print("[VACIA]");
                System.out.println("\n------------------------------------------------------------");

                // 8. Lógica de ESTADO y SALIDA
                estadoActual = (String) statusProc.get("status");
                detalleActual = (String) statusProc.get("details");
                System.out.printf("ESTADO DE %s: %s | %s%n", nombreProceso, estadoActual, detalleActual);
                System.out.println("------------------------------------------------------------");

                if ("COMPLETED".equals(estadoActual) || "REJECTED".equals(estadoActual) || "NOT_FOUND".equals(estadoActual)) {
                    System.out.println("\n>> Proceso terminado. Volviendo al menu principal en 5 segundos...");
                    Thread.sleep(5000); // Pausa para ver el estado final (ahora 5s)
                    break; // Sale del bucle
                }
                
          
                Thread.sleep(TICK_MS);

            } // Fin del while(true)

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); 
            System.out.println(">> [CLIENTE] Monitor interrumpido.");
        } catch (Exception e) {
            System.out.println(">> [CLIENTE] Error de conexion con el servidor: " + e.getMessage());
        }
        
        System.out.println(">> [CLIENTE] Volviendo al menu principal...");
    }
} 
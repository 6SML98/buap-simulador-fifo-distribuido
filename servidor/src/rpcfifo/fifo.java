package rpcfifo;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class fifo {

    // Parámetros
    private static final int MAX_WAIT = 8;    // 8 instancias de 5s
    private static final int TICK_MS = 5000; // 1 tick = 1 s

    // Estado
    private static final List<Proc> FUTURO = new ArrayList<>();
    private static final Queue<Proc> LISTOS = new ConcurrentLinkedQueue<>();
    private static final List<Result> HISTORIAL = new ArrayList<>();
    private static final Set<String> RECHAZADOS = new HashSet<>();
    private static final Map<String, Integer> ULTIMA_ACTIVIDAD = new HashMap<>();
    private static final Set<String> EXPULSADOS = new HashSet<>();
    private static final int TIEMPO_EXPULSION = 10; // 10 instancias de tolerancia

    private static Proc EN_CPU = null;
    private static int AHORA = 0;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    private static ScheduledExecutorService ticker;

    public static final int MAX_INSTANCIAS = 120;
    public static boolean simulacionTerminada = false;
    private int now = 0;

    public static void startSimulationTicker() {
        if (ticker != null && !ticker.isShutdown()) {
            ticker.shutdownNow();
        }
        ticker = Executors.newSingleThreadScheduledExecutor();
        ticker.scheduleAtFixedRate(fifo::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
        System.out.println("SIM inicio: 1 tick = " + (TICK_MS / 1000.0) + " s.");
    }

    private static void tick() {
    synchronized (fifo.class) {
        if (simulacionTerminada) {
            return;
        }

        // 1) Límite de instancias
        if (AHORA >= MAX_INSTANCIAS) {
            simulacionTerminada = true;

            System.out.println("\n[SISTEMA] Se alcanzó el límite máximo de instancias (" + MAX_INSTANCIAS + ").");
            System.out.println("[SISTEMA] Finalizando simulación...");

            // Si hay proceso corriendo, lo cerramos “de golpe”
            if (EN_CPU != null) {
                System.out.println("[FIN-FORZADO] Terminando " + EN_CPU.name + " por fin de simulación.");
                int fin = AHORA;
                int E = EN_CPU.startTime - EN_CPU.C_original;
                Result r = new Result(EN_CPU.name, EN_CPU.startTime, fin, E, EN_CPU.t, EN_CPU.clientId);
                HISTORIAL.add(r);
                EN_CPU = null;
            }
            return;
        }

        // 2) SIEMPRE avanzar el tiempo, haya o no procesos
        AHORA++;

        // ---------- Control de actividad / expulsión de clientes ----------
        Map<String, Integer> procesosActivosPorCliente = new HashMap<>();

        if (EN_CPU != null && EN_CPU.clientId != null) {
            procesosActivosPorCliente.merge(EN_CPU.clientId, 1, Integer::sum);
        }
        for (Proc p : LISTOS) {
            if (p.clientId != null) {
                procesosActivosPorCliente.merge(p.clientId, 1, Integer::sum);
            }
        }
        for (Proc p : FUTURO) {
            if (p.clientId != null) {
                procesosActivosPorCliente.merge(p.clientId, 1, Integer::sum);
            }
        }

        // Clientes con procesos vivos = activos
        for (String clienteActivo : procesosActivosPorCliente.keySet()) {
            if (!EXPULSADOS.contains(clienteActivo)) {
                ULTIMA_ACTIVIDAD.put(clienteActivo, AHORA);
            }
        }

        // Revisar inactividad de quienes ya NO tienen procesos
        List<String> clientesRegistrados = new ArrayList<>(ULTIMA_ACTIVIDAD.keySet());
        for (String cliente : clientesRegistrados) {
            if (procesosActivosPorCliente.containsKey(cliente)) {
                continue; // aún tiene procesos vivos
            }
            int ultima = ULTIMA_ACTIVIDAD.getOrDefault(cliente, 0);
            int inactivo = AHORA - ultima;

            if (inactivo > 0) {
                System.out.printf("[DEBUG] Cliente %s inactivo=%d ticks (sin procesos)%n",
                        cliente, inactivo);
            }

            if (inactivo >= TIEMPO_EXPULSION) {
                EXPULSADOS.add(cliente);
                ULTIMA_ACTIVIDAD.remove(cliente);
                System.out.printf("[SISTEMA] Cliente %s EXPULSADO por inactividad (sin procesos).%n",
                        cliente);
            }
        }

        // ---------- LÓGICA DEL PLANIFICADOR ----------
        // 3) Mover procesos que ya “llegaron” de FUTURO a LISTOS
        moverLlegadas();

        // 4) Si la CPU está libre, intentamos tomar el siguiente de LISTOS
        if (EN_CPU == null) {
            while (!LISTOS.isEmpty()) {
                Proc cabeza = LISTOS.peek();
                int espera = Math.max(0, AHORA - cabeza.C);
                if (espera > MAX_WAIT) {
                    LISTOS.poll();
                    RECHAZADOS.add(cabeza.name);
                    System.out.printf(
                            "[RECHAZO] %s excedio espera (%d > %d) | AHORA=%d%n",
                            cabeza.name, espera, MAX_WAIT, AHORA
                    );
                    continue;
                }
                break;
            }

            EN_CPU = LISTOS.poll();
            if (EN_CPU != null) {
                EN_CPU.startTime = AHORA;
                System.out.printf(
                        "[INICIO] AHORA=%d | %s C=%d t=%d Cliente=%s%n",
                        AHORA, EN_CPU.name, EN_CPU.C, EN_CPU.t, EN_CPU.clientId
                );
            }
        }

        // 5) Si hay proceso en CPU, ver si termina
        if (EN_CPU != null) {
            int fin = EN_CPU.startTime + EN_CPU.t;
            if (AHORA >= fin) {
                int E = EN_CPU.startTime - EN_CPU.C_original;
                Result r = new Result(
                        EN_CPU.name, EN_CPU.startTime, fin, E, EN_CPU.t, EN_CPU.clientId
                );
                HISTORIAL.add(r);
                System.out.printf("[FIN]    AHORA=%d | %s Cliente=%s%n",
                        AHORA, r.name, r.clientId);
                EN_CPU = null;
            } else {
                // aquí podrías logear “sigue ejecutándose” si quieres
            }
        }
    }
}


    private static void moverLlegadas() {
        Iterator<Proc> it = FUTURO.iterator();
        while (it.hasNext()) {
            Proc p = it.next();
            if (p.C <= AHORA) {
                LISTOS.add(p);
                it.remove();
                System.out.printf("[LLEGA] AHORA=%d | %s -> LISTOS (C=%d t=%d)%n",
                        AHORA, p.name, p.C, p.t);
            }
        }
    }

    public boolean reset() {
        synchronized (fifo.class) {
            FUTURO.clear();
            LISTOS.clear();
            HISTORIAL.clear();
            RECHAZADOS.clear();
            EN_CPU = null;
            AHORA = 0;
            SEQ.set(0);
            System.out.println("[REINICIO] AHORA=0.");
            return true;
        }
    }

    public boolean isCPUFree() {
        synchronized (fifo.class) {
            return EN_CPU == null && LISTOS.isEmpty();
        }
    }

    @SuppressWarnings("rawtypes")
    public Map submitProcess(int C, int t, String clientId) { // C y t son instancias
        System.out.println("DEBUG SERVER: Recibido submitProcess -> C=" + C + ", t=" + t + ", ID=" + clientId);
        Map<String, Object> response = new HashMap<>();
        synchronized (fifo.class) {
            if (EXPULSADOS.contains(clientId)) {
                System.out.printf("[ACCESO DENEGADO] El cliente %s intentó entrar pero está expulsado.%n", clientId);
                response.put("status", "ERROR");
                response.put("message", "HAS SIDO EXPULSADO POR INACTIVIDAD. Reinicia tu cliente.");
                return response;
            }

            // Si no está expulsado, actualizamos su actividad para que no lo baneen justo ahora
            ULTIMA_ACTIVIDAD.put(clientId, AHORA);
        }
        // Validación 1: t > 0
        if (t <= 0) {
            System.out.printf("[ENVIO] RECHAZADO (t=%d instancias es inválido)%n", t);
            response.put("status", "ERROR");
            // ... (resto del error igual)
            return response;
        }

        synchronized (fifo.class) {

            // Validación 2: C debe ser futuro (comparando AHORA, que son instancias)
            if (C <= AHORA) {
                System.out.printf("[ENVIO] RECHAZADO (Instancia C=%d inválida) | AHORA=%d%n", C, AHORA);

                response.put("status", "ERROR");
                response.put("reason", "PAST_SCHEDULE");
                response.put("message", "La instancia C=" + C + " ya pasó. (AHORA=" + AHORA + ")");
                response.put("now", AHORA);
                return response;
            }

            String nombre = "P-" + SEQ.incrementAndGet();
            // Creamos el Proc usando los valores de INSTANCIA directamente
            Proc p = new Proc(nombre, C, t, clientId);
            FUTURO.add(p);
            FUTURO.sort(Comparator.naturalOrder());
            System.out.printf("[ENVIO] %s C=%d t=%d -> FUTURO(size=%d) AHORA=%d%n",
                    nombre, p.C, p.t, FUTURO.size(), AHORA);

            response.put("status", "OK");
            response.put("name", nombre);
            return response;
        }
    }

    public String submitProcessNow(int t, int C_original, String clientId) {
        if (t <= 0) {
            System.out.printf("[ENVIO-AHORA] RECHAZADO (t=%d instancias es inválido)%n", t);
            return null;
        }

        synchronized (fifo.class) {
            if (EXPULSADOS.contains(clientId)) {
                System.out.printf("[ACCESO DENEGADO - REENVIO] Cliente %s expulsado.%n", clientId);
                // Retornamos null para indicar fallo (el cliente recibirá null y mostrará error genérico)
                return null;
            }
            // Actualizamos actividad
            ULTIMA_ACTIVIDAD.put(clientId, AHORA);
            int C_efectivo = AHORA; // C es la instancia actual
            String nombre = "P-" + SEQ.incrementAndGet();

            // Creamos el Proc usando los valores de INSTANCIA
            Proc p = new Proc(nombre, C_efectivo, t, clientId);

            // Sobreescribimos C_original con la que el cliente nos pas
            p.C_original = C_original;

            FUTURO.add(p);
            FUTURO.sort(Comparator.naturalOrder());

            System.out.printf("[ENVIO-AHORA] %s (C=%d, C_orig=%d) t=%d -> FUTURO | AHORA=%d%n",
                    nombre, C_efectivo, p.C_original, p.t, AHORA);
            return nombre;
        }
    }

    @SuppressWarnings("rawtypes")
    public Map getProcessStatus(String nombre) {
        Map<String, Object> out = new HashMap<>();
        synchronized (fifo.class) {
            if (RECHAZADOS.contains(nombre)) {
                out.put("status", "REJECTED");
                out.put("details", "Expiro en LISTOS (espera maxima " + MAX_WAIT + ").");
                return out;
            }
            if (EN_CPU != null && EN_CPU.name.equals(nombre)) {
                out.put("status", "RUNNING");
                out.put("details", String.format("En CPU. inicio=%d t=%d AHORA=%d",
                        EN_CPU.startTime, EN_CPU.t, AHORA));
                return out;
            }
            int pos = 1;
            for (Proc p : LISTOS) {
                if (p.name.equals(nombre)) {
                    out.put("status", "READY");
                    out.put("details", String.format("En LISTOS, posicion #%d. AHORA=%d", pos, AHORA));
                    return out;
                }
                pos++;
            }
            for (Proc p : FUTURO) {
                if (p.name.equals(nombre)) {
                    out.put("status", "FUTURE");
                    out.put("details", String.format("Llegara en C=%d. AHORA=%d", p.C, AHORA));
                    return out;
                }
            }
            for (Result r : HISTORIAL) {
                if (r.name.equals(nombre)) {
                    out.put("status", "COMPLETED");
                    out.put("details", String.format("Termino en %d (F=%d E=%d P=%.2f)",
                            r.finish, r.F, r.E, r.P));
                    return out;
                }
            }
            out.put("status", "NOT_FOUND");
            out.put("details", "Proceso no presente en el servidor.");
            return out;
        }
    }

    @SuppressWarnings("rawtypes")
    public Map getDynamicTables() {
        Map<String, Object> state = new HashMap<>();
        synchronized (fifo.class) {
            state.put("now", AHORA);

            Map<String, Object> running = new HashMap<>();
            if (EN_CPU != null) {
                running.put("name", EN_CPU.name);
                running.put("C", EN_CPU.C);
                running.put("t", EN_CPU.t);
                running.put("start", EN_CPU.startTime);
                running.put("clientId", EN_CPU.clientId);
            }
            state.put("running", running.isEmpty() ? null : running);

            List<Map<String, Object>> ready = new ArrayList<>();
            for (Proc p : LISTOS) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", p.name);
                m.put("C", p.C);
                m.put("t", p.t);
                m.put("clientId", p.clientId);
                ready.add(m);
            }
            state.put("ready", ready.toArray());

            List<Map<String, Object>> future = new ArrayList<>();
            for (Proc p : FUTURO) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", p.name);
                m.put("C", p.C);
                m.put("t", p.t);
                m.put("clientId", p.clientId);
                future.add(m);
            }
            state.put("future", future.toArray());
            state.put("rejected", RECHAZADOS.toArray());
            System.out.printf("[TABLAS] AHORA=%d CPU=%s LISTOS=%d FUTURO=%d RECHAZADOS=%d%n",
                    AHORA, (EN_CPU != null ? EN_CPU.name : "INACTIVO"), LISTOS.size(), FUTURO.size(), RECHAZADOS.size());
            return state;
        }
    }

    @SuppressWarnings("rawtypes")
    public Map getSummary() {
        Map<String, Object> res = new HashMap<>();
        synchronized (fifo.class) {
            List<Map<String, Object>> rows = new ArrayList<>();
            double sumE = 0, sumF = 0, sumP = 0;

            for (Result r : HISTORIAL) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", r.name);
                m.put("start", r.start);
                m.put("finish", r.finish);
                m.put("E", r.E);
                m.put("t", r.t);
                m.put("F", r.F);
                m.put("P", r.P);
                m.put("clientId", r.clientId);
                rows.add(m);
                sumE += r.E;
                sumF += r.F;
                sumP += r.P;
            }
            int n = Math.max(1, HISTORIAL.size());
            res.put("rows", rows.toArray());
            res.put("avgE", sumE / n);
            res.put("avgF", sumF / n);
            res.put("avgP", sumP / n);
            System.out.printf("[RESUMEN] n=%d promE=%.2f promF=%.2f promP=%.2f%n",
                    HISTORIAL.size(), sumE / n, sumF / n, sumP / n);
            return res;
        }
    }

    private static String repetirCaracter(char c, int n) {
        if (n <= 0) {
            return "";
        }
        char[] buf = new char[n];
        Arrays.fill(buf, c);
        return new String(buf);
    }

    public String getMonitorServidor() {
        StringBuilder sb = new StringBuilder();

        synchronized (fifo.class) {
            sb.append("=================== MONITOR DEL SERVIDOR ===================\n");
            // AHORA ahora son instancias (cada 5 segundos)
            sb.append(" INSTANCIA (AHORA): " + AHORA + " (Tick cada 5s)\n");
            sb.append("----------------------------------------------------------\n");
            // ¡NUEVA LÍNEA DE TIEMPO!
            sb.append("Proceso |0..1..2..3..4..5..6..7..8..9..10.11.12.13.14.15.16\n");
            sb.append("--------|--|--|--|--|--|--|--|--|--|--|--|--|--|--|--|--|\n");

            // 1. Dibujar Procesos TERMINADOS (Gantt)
            List<Result> historialCopia = new ArrayList<>(HISTORIAL);
            for (Result r : historialCopia) {
                // La lógica de barras ahora funciona multiplicando por 3 para espaciar
                String barraEspera = repetirCaracter('.', r.start * 3);
                String barraEjec = repetirCaracter('#', r.t * 3);
                sb.append(String.format("%-7s |%s%s (Fin: %d)%n", r.name, barraEspera, barraEjec, r.finish));
            }

            // 2. Dibujar Proceso EN_CPU (Gantt)
            if (EN_CPU != null) {
                int transcurrido = Math.max(0, AHORA - EN_CPU.startTime);
                int restante = Math.max(0, EN_CPU.t - transcurrido);

                String barraEspera = repetirCaracter('.', EN_CPU.startTime * 3);
                String barraEjec = repetirCaracter('#', transcurrido * 3);
                String barraFutura = repetirCaracter('=', restante * 3);

                sb.append(String.format("%-7s |%s%s%s (Corriendo)%n", EN_CPU.name, barraEspera, barraEjec, barraFutura));
            }

            sb.append("\n--- Colas del Sistema ---\n");
            sb.append(String.format("LISTOS (%d): ", LISTOS.size()));
            if (LISTOS.isEmpty()) {
                sb.append("[VACIA]");
            } else {
                for (Proc p : LISTOS) {
                    sb.append(String.format("%s(t=%d) ", p.name, p.t));
                }
            }
            sb.append("\n");
            sb.append(String.format("FUTURO (%d): ", FUTURO.size()));
            if (FUTURO.isEmpty()) {
                sb.append("[VACIA]");
            } else {
                for (Proc p : FUTURO) {
                    sb.append(String.format("%s(C=%d) ", p.name, p.C));
                }
            }
            sb.append("\n");
            sb.append(String.format("RECHAZADOS (%d): ", RECHAZADOS.size()));
            if (RECHAZADOS.isEmpty()) {
                sb.append("[VACIO]");
            } else {
                sb.append(String.join(", ", RECHAZADOS));
            }
            sb.append("\n");
            sb.append("\n--- Resumen de Procesos Terminados ---\n");
            if (historialCopia.isEmpty()) {
                sb.append("[Aún no hay procesos terminados]\n");
            } else {
                sb.append(String.format("%-7s | %-6s | %-4s | %-3s | %-3s | %-3s | %-4s%n",
                        "Proceso", "Inicio", "Fin", "E", "t", "F", "P"));
                sb.append("----------------------------------------------------------\n");
                double sumE = 0, sumF = 0, sumP = 0;
                for (Result r : historialCopia) {
                    sb.append(String.format("%-7s | %-6d | %-4d | %-3d | %-3d | %-3d | %-4.2f%n",
                            r.name, r.start, r.finish, r.E, r.t, r.F, r.P));
                    sumE += r.E;
                    sumF += r.F;
                    sumP += r.P;
                }
                int n = historialCopia.size();
                sb.append("----------------------------------------------------------\n");
                sb.append(String.format("%-7s | %-6s | %-4s | %-3.2f | %-3s | %-3.2f | %-4.2f%n",
                        "MEDIA", "", "", (sumE / n), "", (sumF / n), (sumP / n)));
            }
            sb.append("==========================================================\n");
        }
        return sb.toString();
    }

    public boolean login(String clientId) {
        synchronized (fifo.class) {
            if (EXPULSADOS.contains(clientId)) {
                return false; // Está baneado
            }
            // Registramos "último uso" en esta instancia
            ULTIMA_ACTIVIDAD.put(clientId, AHORA);
            System.out.printf("[CONEXION] Cliente %s se ha conectado.%n", clientId);
            return true;
        }
    }

    public boolean recibirResumenCliente(String clientId, Object[] resumen) {
        synchronized (fifo.class) {
            System.out.println("\n==================================================");
            System.out.println("   REPORTE FINAL RECIBIDO DE: " + clientId);
            System.out.println("==================================================");
            System.out.printf("%-7s | %-6s | %-6s | %-4s | %-4s | %-4s | %-4s%n",
                    "Nombre", "Inicio", "Fin", "E", "t", "F", "P");
            System.out.println("--------------------------------------------------");

            // Recorremos los datos que nos mandó el cliente
            for (Object o : resumen) {
                Map<String, Object> r = (Map<String, Object>) o;
                System.out.printf("%-7s | %-6s | %-6s | %-4s | %-4s | %-4s | %-4s%n",
                        r.get("name"), r.get("start"), r.get("finish"),
                        r.get("E"), r.get("t"), r.get("F"), r.get("P"));
            }
            System.out.println("==================================================\n");

            // Aquí ya queda consolidado porque el servidor lo imprimió en su log global
            return true;
        }
    }

    public boolean isExpelled(String clientId) {
        synchronized (fifo.class) {
            return EXPULSADOS.contains(clientId);
        }
    }
}

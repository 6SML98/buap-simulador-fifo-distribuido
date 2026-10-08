package rpcfifo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class ClienteSwing extends JFrame {

    private JTextField txtC;
    private JTextField txtT;
    private JButton btnEnviar;
    private JLabel lblAhora;
    private GanttChartPanel ganttPanel;
    private JTextArea txtLog;
    private JTable tblListos;
    private JTable tblFuturo;
    private DefaultTableModel modelListos;
    private DefaultTableModel modelFuturo;
    private JTable tblHistorial;
    private DefaultTableModel modelHistorial;
    private String clientId;
    private JTextArea txtRechazadosCliente;

    // Genera un ID aleatorio al iniciar la ventana (ej. "Cliente-482")
    //logica de Conexon
    private XmlRpcClient client;
    private Timer timer;
    private static final String SERVER_URL = "http://localhost:8080/";

    private final List<String> misProcesos = new ArrayList<>();
    private final Map<String, String> statusMap = new HashMap<>();

    public ClienteSwing() {
        // 1. Configurar la ventana principal
        super("Cliente del Planificador FIFO");
        clientId = "Cliente-" + (int) (Math.random() * 10000); // Genera un ID único para cada instancia de cliente
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationByPlatform(true);
        setLayout(new BorderLayout());

        // 2. Crear todos los componentes visuales
        initComponents();

        // 3. Conectarse al servidor
        initRpc();

        // 4. Iniciar el temporizador de refresco
        initTimer();

        log("Cliente conectado a " + SERVER_URL);
    }

    private void initComponents() {

        // 1. Panel de Inputs 
        JPanel panelInput = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelInput.setBorder(BorderFactory.createTitledBorder("Nuevo Proceso"));

        panelInput.add(new JLabel("Instancia (C):"));
        txtC = new JTextField(5);
        panelInput.add(txtC);

        panelInput.add(new JLabel("Duración (t):"));
        txtT = new JTextField(5);
        panelInput.add(txtT);

        btnEnviar = new JButton("Enviar Proceso");
        panelInput.add(btnEnviar);

        lblAhora = new JLabel("AHORA: 0");
        lblAhora.setFont(new Font("Arial", Font.BOLD, 14));
        panelInput.add(new JLabel("     |     ")); // Separador
        panelInput.add(lblAhora);

        add(panelInput, BorderLayout.NORTH);

        // Panel de Gantt 
        ganttPanel = new GanttChartPanel();
        JScrollPane scrollGantt = new JScrollPane(ganttPanel);
        scrollGantt.setBorder(BorderFactory.createTitledBorder("Mis Procesos"));

        //3. Panel de Colas 
        modelListos = new DefaultTableModel(new Object[]{"Nombre", "C (Llegada)", "t"}, 0);
        tblListos = new JTable(modelListos);
        JScrollPane scrollListos = new JScrollPane(tblListos);
        scrollListos.setBorder(BorderFactory.createTitledBorder("COLA DE ESPERA GLOBAL (FIFO)"));

        modelFuturo = new DefaultTableModel(new Object[]{"Nombre", "C (Llegada)", "t"}, 0);
        tblFuturo = new JTable(modelFuturo);
        JScrollPane scrollFuturo = new JScrollPane(tblFuturo);
        scrollFuturo.setBorder(BorderFactory.createTitledBorder("COLA DE ESPERA (Mis Procesos)"));

        modelHistorial = new DefaultTableModel(new Object[]{"Nombre", "Inicio", "Fin", "E", "t", "F", "P"}, 0);
        tblHistorial = new JTable(modelHistorial);
        JScrollPane scrollHistorial = new JScrollPane(tblHistorial);
        scrollHistorial.setBorder(BorderFactory.createTitledBorder("HISTORIAL (Mis Procesos)"));
        txtRechazadosCliente = new JTextArea(5, 0);
        txtRechazadosCliente.setEditable(false);
        txtRechazadosCliente.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollRechazadosCli = new JScrollPane(txtRechazadosCliente);
        scrollRechazadosCli.setBorder(
                BorderFactory.createTitledBorder("RECHAZADOS (Mis Procesos)"));

        // Panel de Log 
        txtLog = new JTextArea(8, 0);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Registro de Actividad"));

        JPanel panelQueues = new JPanel(new java.awt.GridLayout(1, 3));
        //panelQueues.add(scrollListos);
        panelQueues.add(scrollFuturo);
        panelQueues.add(scrollHistorial);
        panelQueues.add(scrollRechazadosCli);

        // Divisor para las colas y el log
        javax.swing.JSplitPane bottomSplit = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, panelQueues, scrollLog);
        bottomSplit.setDividerLocation(150);
        bottomSplit.setResizeWeight(0.5);

        // Divisor principal para el Gantt y el panel inferior
        javax.swing.JSplitPane mainSplit = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, scrollGantt, bottomSplit);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.4);

        add(mainSplit, BorderLayout.CENTER);

        btnEnviar.addActionListener(e -> enviarProceso());
    }

    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(mensaje + "\n");

            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private void initRpc() {
        try {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(SERVER_URL));
            config.setEnabledForExtensions(true);
            client = new XmlRpcClient();
            client.setConfig(config);

            // --- ¡NUEVO! SALUDAR AL SERVIDOR ---
            // Esto hace que el servidor sepa que existimos y empiece a contar el tiempo
            try {
                client.execute("Scheduler.login", new Object[]{this.clientId});
                log("Conectado y registrado en el servidor como: " + this.clientId);
            } catch (Exception e) {
                log("Advertencia: No se pudo registrar sesión en el servidor.");
            }
            // -----------------------------------

        } catch (Exception e) {
            log("Error fatal de conexión: " + e.getMessage());
        }
    }

    private void initTimer() {
        timer = new Timer(5000, e -> actualizarDatos());
        timer.setInitialDelay(1000);
        timer.start();
    }

    private void enviarProceso() {
        int C, t;
        try {
            // 1. Validar Instancia (C)
            C = Integer.parseInt(txtC.getText());
            if (C < 0) {
                JOptionPane.showMessageDialog(this, "La Instancia (C) no puede ser negativa.", "Error de Entrada", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2. Validar Duración (t)
            String t_input = txtT.getText();
            if (t_input.equalsIgnoreCase("r")) {
                t = (int) (Math.random() * 4) + 1;
                log("Usando duración aleatoria t=" + t);
            } else {
                t = Integer.parseInt(t_input);
                if (t <= 0) {
                    JOptionPane.showMessageDialog(this, "La Duración (t) debe ser mayor a 0.", "Error de Entrada", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Por favor ingresa solo números enteros válidos.", "Error de Formato", JOptionPane.ERROR_MESSAGE);
            return;
        }

        log("Enviando C=" + C + ", t=" + t + "...");

        try {
            // CAMBIO AQUÍ: Enviamos C, t, Y EL CLIENTID
            Map<String, Object> response = (Map<String, Object>) client.execute(
                    "Scheduler.submitProcess", new Object[]{C, t, clientId}
            );

            String status = (String) response.get("status");
            if ("OK".equals(status)) {
                String nombre = (String) response.get("name");
                log("¡Éxito! Proceso enviado. Nombre asignado: " + nombre);
                misProcesos.add(nombre);
                statusMap.put(nombre, "ENVIADO");
                actualizarDatos();
            } else {
                String reason = (String) response.get("reason");
                if ("PAST_SCHEDULE".equals(reason)) {
                    int serverNow = (Integer) response.get("now");
                    String msg = "La instancia C=" + C + " ya pasó (Servidor en AHORA=" + serverNow + ").\n"
                            + "¿Deseas re-enviar para la próxima instancia disponible (AHORA)?";

                    int opcion = JOptionPane.showConfirmDialog(this, msg, "Error: Tarea Pasada", JOptionPane.YES_NO_OPTION);

                    if (opcion == JOptionPane.YES_OPTION) {
                        log("Re-enviando (t=" + t + ", C_original=" + C + ") para la instancia actual: " + serverNow);

                        // CAMBIO AQUÍ TAMBIÉN: Enviamos t, C original, Y EL CLIENTID
                        String nombreNuevo = (String) client.execute(
                                "Scheduler.submitProcessNow", new Object[]{t, C, clientId}
                        );

                        if (nombreNuevo != null) {
                            log("¡Éxito! Proceso re-enviado. Nombre: " + nombreNuevo);
                            misProcesos.add(nombreNuevo);
                            statusMap.put(nombreNuevo, "ENVIADO");
                            actualizarDatos();
                        } else {
                            log("Error al re-enviar el proceso.");
                        }
                    } else {
                        log("Envío cancelado por el usuario.");
                    }
                } else {
                    log("Error del servidor: " + response.get("message"));
                }
            }
        } catch (Exception e) {
            log("Error de conexión RPC: " + e.getMessage());
        }
    }

    private void actualizarDatos() {
        if (client == null) {
            return;
        }

        try {
            // 1) Intentar preguntar si la simulación terminó.
            // Si el servidor NO tiene ese handler, silenciosamente lo ignoramos.
            boolean terminado = false;
            try {
                terminado = (boolean) client.execute("Scheduler.simulacionTerminada", new Object[]{});
            } catch (Exception exSim) {
                // Handler no existe todavía -> lo ignoramos
                terminado = false;
            }

            if (terminado) {
                enviarReporteFinal();
                JOptionPane.showMessageDialog(
                        this,
                        "Simulación finalizada por el servidor.",
                        "FIN",
                        JOptionPane.INFORMATION_MESSAGE
                );
                System.exit(0);
            }

            // 2) ¿Este cliente fue expulsado por inactividad?
            boolean estoyExpulsado = (boolean) client.execute(
                    "Scheduler.isExpelled",
                    new Object[]{this.clientId}
            );

            if (estoyExpulsado) {
                timer.stop();
                enviarReporteFinal();
                JOptionPane.showMessageDialog(
                        this,
                        "Has sido desconectado por inactividad (10 instancias sin procesos).",
                        "Expulsado",
                        JOptionPane.WARNING_MESSAGE
                );
                System.exit(0);
            }

            // 3) Pedir estado completo al servidor
            Map<String, Object> fullState
                    = (Map<String, Object>) client.execute("Scheduler.getDynamicTables", Collections.emptyList());
            Map<String, Object> fullSummary
                    = (Map<String, Object>) client.execute("Scheduler.getSummary", Collections.emptyList());

            // 4) Actualizar reloj local
            int ahora = (Integer) fullState.get("now");
            lblAhora.setText("AHORA: " + ahora);

            // 5) Filtrar para quedarnos sólo con MIS procesos
            Map<String, Object> filteredState = filterState(fullState);
            Map<String, Object> filteredSummary = filterSummary(fullSummary);

            // 6) Actualizar el Gantt (sólo mis procesos)
            ganttPanel.actualizarDatos(filteredState, filteredSummary);
            ganttPanel.repaint();

            // 7) Revisar cambios de estado proceso por proceso para el log
            List<String> procesosAChecar = new ArrayList<>(misProcesos);
            for (String nombre : procesosAChecar) {
                Map<String, Object> statusResult = (Map<String, Object>) client.execute(
                        "Scheduler.getProcessStatus", new Object[]{nombre}
                );

                String nuevoStatus = (String) statusResult.get("status");
                String nuevoDetalle = (String) statusResult.get("details");
                String ultimoStatus = statusMap.getOrDefault(nombre, "ENVIADO");

                if (!nuevoStatus.equals(ultimoStatus)) {
                    String logMsg = ">> " + nombre + " cambió a: " + nuevoStatus;

                    if ("READY".equals(nuevoStatus)) {
                        logMsg += " (Esperando en la fila. " + nuevoDetalle + ")";
                        Map<String, Object> runningProc = (Map<String, Object>) fullState.get("running");
                        if (runningProc != null) {
                            String runningName = (String) runningProc.get("name");
                            logMsg += "\n    -> MOTIVO: La CPU está ocupada por "
                                    + runningName + ". Tu proceso se ejecutará cuando se libere.";
                        }
                    } else if ("RUNNING".equals(nuevoStatus)) {
                        logMsg += " (¡En ejecución! " + nuevoDetalle + ")";
                    } else if ("COMPLETED".equals(nuevoStatus)) {
                        logMsg += " (" + nuevoDetalle + ")";
                    } else {
                        logMsg += " (" + nuevoDetalle + ")";
                    }

                    log(logMsg);
                    statusMap.put(nombre, nuevoStatus);
                }
            }

            // 8) Cola global (en el cliente la dejamos vacía)
            modelListos.setRowCount(0);

            // 9) COLA DE ESPERA (Mis Procesos) = READY + FUTURE filtrados
            modelFuturo.setRowCount(0);

            Object[] readyMine = (Object[]) filteredState.get("ready");
            if (readyMine != null) {
                for (Object obj : readyMine) {
                    Map<String, Object> p = (Map<String, Object>) obj;
                    modelFuturo.addRow(new Object[]{p.get("name"), p.get("C"), p.get("t")});
                }
            }

            Object[] futureMine = (Object[]) filteredState.get("future");
            if (futureMine != null) {
                for (Object obj : futureMine) {
                    Map<String, Object> p = (Map<String, Object>) obj;
                    modelFuturo.addRow(new Object[]{p.get("name"), p.get("C"), p.get("t")});
                }
            }

// 10) RECHAZADOS (Mis Procesos)
            txtRechazadosCliente.setText("");
            Object[] myRejected = (Object[]) filteredState.get("rejected");
            if (myRejected != null && myRejected.length > 0) {
                for (Object obj : myRejected) {
                    String name = (String) obj;     // en fullState.rejected sólo vienen nombres
                    txtRechazadosCliente.append(name + "\n");
                }
            } else {
                txtRechazadosCliente.setText("[VACÍO]");
            }

// 11) HISTORIAL (Mis Procesos) – lo calcula el servidor
            modelHistorial.setRowCount(0);
            Object[] filteredRows = (Object[]) filteredSummary.get("rows");
            if (filteredRows != null) {
                for (Object rowObj : filteredRows) {
                    Map<String, Object> r = (Map<String, Object>) rowObj;
                    modelHistorial.addRow(new Object[]{
                        r.get("name"),
                        r.get("start"),
                        r.get("finish"),
                        r.get("E"),
                        r.get("t"),
                        r.get("F"),
                        String.format("%.2f", r.get("P"))
                    });
                }
            }

        } catch (Exception e) {
            log("Error al refrescar datos: " + e.getMessage());
            // timer.stop(); // si quieres detener el timer en caso de error grave
        }
    }

   @SuppressWarnings("unchecked")
private Map<String, Object> filterState(Map<String, Object> fullState) {
    Map<String, Object> filtered = new HashMap<>();

    filtered.put("now", fullState.get("now"));

    // RUNNING solo si es mío
    Map<String, Object> running = (Map<String, Object>) fullState.get("running");
    if (running != null && misProcesos.contains((String) running.get("name"))) {
        filtered.put("running", running);
    } else {
        filtered.put("running", null);
    }

    // READY solo mis procesos
    List<Map<String, Object>> readyMine = new ArrayList<>();
    Object[] ready = (Object[]) fullState.get("ready");
    if (ready != null) {
        for (Object obj : ready) {
            Map<String, Object> p = (Map<String, Object>) obj;
            if (misProcesos.contains((String) p.get("name"))) {
                readyMine.add(p);
            }
        }
    }
    filtered.put("ready", readyMine.toArray());

    // FUTURE solo mis procesos
    List<Map<String, Object>> futureMine = new ArrayList<>();
    Object[] future = (Object[]) fullState.get("future");
    if (future != null) {
        for (Object obj : future) {
            Map<String, Object> p = (Map<String, Object>) obj;
            if (misProcesos.contains((String) p.get("name"))) {
                futureMine.add(p);
            }
        }
    }
    filtered.put("future", futureMine.toArray());

    // REJECTED: en fullState.rejected vienen solo nombres
    Object[] rej = (Object[]) fullState.get("rejected");
    List<String> rejMine = new ArrayList<>();
    if (rej != null) {
        for (Object obj : rej) {
            String name = (String) obj;
            if (misProcesos.contains(name)) {
                rejMine.add(name);
            }
        }
    }
    filtered.put("rejected", rejMine.toArray());

    return filtered;
}


    private Map<String, Object> filterSummary(Map<String, Object> fullSummary) {
        Map<String, Object> filtered = new HashMap<>(fullSummary);
        List<Object> filteredRows = new ArrayList<>();

        Object[] allRows = (Object[]) fullSummary.get("rows");
        for (Object rowObj : allRows) {
            Map<String, Object> r = (Map<String, Object>) rowObj;
            if (misProcesos.contains(r.get("name"))) {
                filteredRows.add(rowObj);
            }
        }

        filtered.put("rows", filteredRows.toArray());
        return filtered;
    }

    private List<Object> filterQueue(Object[] fullQueue) {
        List<Object> filtered = new ArrayList<>();
        for (Object procObj : fullQueue) {
            Map<String, Object> p = (Map<String, Object>) procObj;
            if (misProcesos.contains(p.get("name"))) {
                filtered.add(procObj);
            }
        }
        return filtered;
    }

    /**
     * El método main() para ejecutar esta ventana de cliente.
     */
    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(() -> {
            new ClienteSwing().setVisible(true);
        });
    }

    /**
     * Recopila los datos de la tabla Historial y los manda al servidor.
     */
    private void enviarReporteFinal() {
        try {
            // 1. Sacar los datos de nuestro modelo local de Historial
            List<Map<String, Object>> listaResumen = new ArrayList<>();

            // Iteramos sobre las filas de la tabla visual del cliente
            for (int i = 0; i < modelHistorial.getRowCount(); i++) {
                Map<String, Object> fila = new HashMap<>();
                // El orden de las columnas en tu modelo es: 
                // 0:Nombre, 1:Inicio, 2:Fin, 3:E, 4:t, 5:F, 6:P
                fila.put("name", modelHistorial.getValueAt(i, 0));
                fila.put("start", modelHistorial.getValueAt(i, 1));
                fila.put("finish", modelHistorial.getValueAt(i, 2));
                fila.put("E", modelHistorial.getValueAt(i, 3));
                fila.put("t", modelHistorial.getValueAt(i, 4));
                fila.put("F", modelHistorial.getValueAt(i, 5));
                fila.put("P", modelHistorial.getValueAt(i, 6));

                listaResumen.add(fila);
            }

            // 2. Enviar al servidor
            log("Enviando resumen final al servidor...");
            client.execute("Scheduler.recibirResumenCliente", new Object[]{this.clientId, listaResumen.toArray()});

        } catch (Exception e) {
            System.out.println("Error al enviar reporte final: " + e.getMessage());
        }
    }
}

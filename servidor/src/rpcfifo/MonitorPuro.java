package rpcfifo;

// 1. Importaciones necesarias (Swing, AWT, RPC, etc.)
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

public class MonitorPuro extends JFrame {

    private JLabel lblAhora;
    private JTable tblListos;
    private JTable tblFuturo;
    private JTable tblHistorial;
    private GanttChartPanel ganttPanel;

    private JTextArea txtGantt;
    private JTextArea txtRechazados;
    // --- Modelos de las Tablas ---
    //private DefaultTableModel modelListos;
    //private DefaultTableModel modelFuturo;
    private DefaultTableModel modelHistorial;
    private DefaultTableModel modelCola;
    private JTable tblCola;

    // --- Lógica de Conexión ---
    private XmlRpcClient client;
    private Timer timer;
    private static final String SERVER_URL = "http://localhost:8080/";

    /**
     * Constructor principal
     */
    public MonitorPuro() {
        // 1. Configurar la ventana principal (el JFrame)
        super("Monitor del Servidor FIFO (100% Código)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600); // Tamaño de la ventana
        setLocationRelativeTo(null); // Centrar en pantalla
        setLayout(new BorderLayout()); // Usaremos un layout simple

        // 2. Crear todos los componentes visuales
        crearComponentes();

        // 3. Conectarse al servidor
        iniciarClienteRPC();

        // 4. Iniciar el temporizador de refresco
        iniciarTimer();
    }

    /**
     * Crea y organiza todos los botones, tablas y etiquetas.
     */
    /**
     * Crea y organiza todos los botones, tablas y etiquetas.
     */
    private void crearComponentes() {
        // --- A. El Reloj (Etiqueta de Arriba) ---
        lblAhora = new JLabel("Conectando...", SwingConstants.CENTER); // Centrado
        lblAhora.setFont(new Font("Arial", Font.BOLD, 20)); // Fuente más grande
        add(lblAhora, BorderLayout.NORTH); // El reloj sigue arriba

        // --- B. Las Tablas y Paneles ---
        modelCola = new DefaultTableModel(new Object[]{"Nombre", "C", "t"}, 0);
        tblCola = new JTable(modelCola);
        JScrollPane scrollCola = new JScrollPane(tblCola);
        scrollCola.setBorder(BorderFactory.createTitledBorder("COLA DE ESPERA (FIFO)"));

        // Modelos de Tabla (con títulos corregidos)
        //modelListos = new DefaultTableModel(new Object[]{"Nombre", "C", "t"}, 0);
        //modelFuturo = new DefaultTableModel(new Object[]{"Nombre", "C", "t"}, 0);
        modelHistorial = new DefaultTableModel(new Object[]{"Nombre", "Inicio", "Fin", "E (Espera)", "t", "F (Total)", "P"}, 0);

        // Tablas
        //tblListos = new JTable(modelListos);
        //tblFuturo = new JTable(modelFuturo);
        tblHistorial = new JTable(modelHistorial);

        // Paneles de Scroll para las tablas
        //JScrollPane scrollListos = new JScrollPane(tblListos);
        //scrollListos.setBorder(BorderFactory.createTitledBorder("COLA DE ESPERA (FIFO)"));
        //JScrollPane scrollFuturo = new JScrollPane(tblFuturo);
        // scrollFuturo.setBorder(BorderFactory.createTitledBorder("FUTURO"));
        JScrollPane scrollHistorial = new JScrollPane(tblHistorial);
        scrollHistorial.setBorder(BorderFactory.createTitledBorder("TABLA FINALIZACIÓN / PENALIZACIÓN"));

        // ¡NUEVO! Panel de texto para RECHAZADOS
        txtRechazados = new JTextArea();
        txtRechazados.setEditable(false);
        txtRechazados.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtRechazados.setForeground(Color.RED);
        JScrollPane scrollRechazados = new JScrollPane(txtRechazados);
        scrollRechazados.setBorder(BorderFactory.createTitledBorder("RECHAZADOS"));

        // --- C. El Panel Central (con 4 columnas) ---
        JPanel panelCentral = new JPanel(new GridLayout(1, 4)); // 4 columnas
        //panelCentral.add(scrollListos);
        //panelCentral.add(scrollFuturo);
        panelCentral.add(scrollCola);
        panelCentral.add(scrollHistorial);
        panelCentral.add(scrollRechazados); // Añadido

        // --- D. El Gantt Chart (Panel de Arriba) ---
        ganttPanel = new GanttChartPanel(); // Crea nuestro panel de dibujo
        ganttPanel.setModoCiclico(true);
        JScrollPane scrollGantt = new JScrollPane(ganttPanel);
        scrollGantt.setBorder(BorderFactory.createTitledBorder("GRÁFICO DE PROCESOS (Gantt)"));
        scrollGantt.setPreferredSize(new Dimension(800, 250));

        // --- E. El Panel Divisor ---
        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, // División vertical
                scrollGantt, // Componente de ARRIBA
                panelCentral // Componente de ABAJO (¡usa panelCentral!)
        );
        splitPane.setDividerLocation(300); // Le damos más espacio al Gantt

        // Agregamos el divisor al CENTRO de la ventana
        add(splitPane, BorderLayout.CENTER);
    }

    private void iniciarClienteRPC() {
        try {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(SERVER_URL));
            config.setEnabledForExtensions(true);
            client = new XmlRpcClient();
            client.setConfig(config);
            System.out.println("MonitorPuro: Conectado a " + SERVER_URL);
        } catch (Exception e) {
            System.err.println("MonitorPuro: Error al conectar con el servidor RPC: " + e.getMessage());
            lblAhora.setText("Error de conexión");
        }
    }

    private void iniciarTimer() {
        timer = new Timer(5000, e -> actualizarDatos()); // 5 segundos
        timer.setInitialDelay(1000); // 1 segundo la primera vez
        timer.start();
    }

    private void actualizarDatos() {
        if (client == null) {
            return;
        }

        try {
            // Pide los datos al servidor
            Map<String, Object> state = (Map<String, Object>) client.execute("Scheduler.getDynamicTables", Collections.emptyList());
            Map<String, Object> summary = (Map<String, Object>) client.execute("Scheduler.getSummary", Collections.emptyList());

            // Actualiza el Panel Gantt
            ganttPanel.actualizarDatos(state, summary);
            ganttPanel.repaint(); // Forza al panel a redibujarse

            // Actualizar Reloj (con el texto corregido)
            int ahora = (Integer) state.get("now");
            lblAhora.setText("INSTANCIA (AHORA): " + ahora + "  (1 Instancia = 5s)");

            // Actualizar Tabla LISTOS
            /**
             * modelListos.setRowCount(0); // Borra los datos viejos Object[]
             * ready = (Object[]) state.get("ready"); for (Object procObj :
             * ready) { Map<String, Object> p = (Map<String, Object>) procObj;
             * modelListos.addRow(new Object[]{p.get("name"), p.get("C"),
             * p.get("t")}); }
             */
            modelCola.setRowCount(0);

// a) Procesos en READY (ya llegaron y esperan)
            Object[] ready = (Object[]) state.get("ready");
            if (ready != null) {
                for (Object procObj : ready) {
                    Map<String, Object> p = (Map<String, Object>) procObj;
                    modelCola.addRow(new Object[]{p.get("name"), p.get("C"), p.get("t")});
                }
            }

            // Actualizar Tabla FUTURO
        
            Object[] future = (Object[]) state.get("future");
            for (Object procObj : future) {
                Map<String, Object> p = (Map<String, Object>) procObj;
                modelCola.addRow(new Object[]{p.get("name"), p.get("C"), p.get("t")});
            }

            // Actualizar Tabla HISTORIAL
            modelHistorial.setRowCount(0);
            Object[] rows = (Object[]) summary.get("rows");
            for (Object rowObj : rows) {
                Map<String, Object> r = (Map<String, Object>) rowObj;
                modelHistorial.addRow(new Object[]{
                    r.get("name"), r.get("start"), r.get("finish"),
                    r.get("E"), r.get("t"), r.get("F"),
                    String.format("%.2f", r.get("P"))
                });
            }

            // Actualizar Lista de RECHAZADOS
            Object[] rejected = (Object[]) state.get("rejected");
            txtRechazados.setText(""); // Limpia datos viejos
            if (rejected.length == 0) {
                txtRechazados.setText("[VACIO]");
            } else {
                for (Object procName : rejected) {
                    txtRechazados.append(procName.toString() + "\n");
                }
            }

        } catch (Exception e) {
            System.err.println("MonitorPuro: Error al actualizar datos: " + e.getMessage());
            lblAhora.setText("Error de actualización");
        }
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MonitorPuro().setVisible(true);
            }
        });
    }
}

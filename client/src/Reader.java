import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.net.Socket;

public class Reader extends JFrame {
    int selectedAnswer = 0; // выбранный ответ 1..4
    JRadioButton flag1, flag2, flag3, flag4;
    ButtonGroup bg;
    JButton b;

    JTextArea l1 = new JTextArea("Вопрос");
    JTextArea l2 = new JTextArea("Вариант 1");
    JTextArea l3 = new JTextArea("Вариант 2");
    JTextArea l4 = new JTextArea("Вариант 3");
    JTextArea l5 = new JTextArea("Вариант 4");

    JTable leaderboardTable;
    DefaultTableModel leaderboardModel;

    // Сетевые поля
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String userName;

    public Reader(String title, String serverIp, int serverPort) {
        super(title);
        setSize(900, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Левая панель
        JPanel leftPanel = new JPanel(null);
        leftPanel.setPreferredSize(new Dimension(600, 450));

        b = new JButton("Ответить");
        flag1 = new JRadioButton("1");
        flag2 = new JRadioButton("2");
        flag3 = new JRadioButton("3");
        flag4 = new JRadioButton("4");
        bg = new ButtonGroup();
        bg.add(flag1);
        bg.add(flag2);
        bg.add(flag3);
        bg.add(flag4);

        setupTextArea(l1, 480, 60, 70, 50);
        setupTextArea(l2, 220, 60, 70, 160);
        setupTextArea(l3, 220, 60, 350, 160);
        setupTextArea(l4, 220, 60, 70, 280);
        setupTextArea(l5, 220, 60, 350, 280);

        b.setSize(200, 30);
        b.setLocation(370, 365);

        flag1.setSize(40, 25);
        flag1.setLocation(30, 160);
        flag2.setSize(40, 25);
        flag2.setLocation(310, 160);
        flag3.setSize(40, 25);
        flag3.setLocation(30, 280);
        flag4.setSize(40, 25);
        flag4.setLocation(310, 280);

        leftPanel.add(b);
        leftPanel.add(l1);
        leftPanel.add(l2);
        leftPanel.add(l3);
        leftPanel.add(l4);
        leftPanel.add(l5);
        leftPanel.add(flag1);
        leftPanel.add(flag2);
        leftPanel.add(flag3);
        leftPanel.add(flag4);

        b.addActionListener(new ButtonActionListener());
        flag1.addActionListener(e -> selectedAnswer = 1);
        flag2.addActionListener(e -> selectedAnswer = 2);
        flag3.addActionListener(e -> selectedAnswer = 3);
        flag4.addActionListener(e -> selectedAnswer = 4);

        // Правая панель - таблица лидеров
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(265, 450));

        JLabel leaderboardLabel = new JLabel("Таблица лидеров", SwingConstants.CENTER);
        leaderboardLabel.setFont(new Font("Arial", Font.BOLD, 18));
        leaderboardLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        rightPanel.add(leaderboardLabel, BorderLayout.NORTH);

        leaderboardModel = new DefaultTableModel(new Object[]{"Место", "Имя", "Очки"}, 0);
        leaderboardTable = new JTable(leaderboardModel);
        leaderboardTable.setFillsViewportHeight(true);
        leaderboardTable.setRowHeight(25);

        JScrollPane scrollPane = new JScrollPane(leaderboardTable);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);

        // Запрос имени пользователя
        userName = askUserName();

        // Подключение к серверу и запуск слушателя сообщений
        connectToServer(serverIp, serverPort);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setupTextArea(JTextArea area, int width, int height, int x, int y) {
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Arial", Font.PLAIN, 14));
        area.setBackground(UIManager.getColor("Label.background"));
        area.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        area.setBounds(x, y, width, height);
    }

    private String askUserName() {
        String name = null;

        name = JOptionPane.showInputDialog(this, "Введите ваше имя:", "Приветствие", JOptionPane.PLAIN_MESSAGE);
        if (name == null) { // пользователь нажал Отмена
            System.exit(0);
        }

        return name.trim();
    }

    private void connectToServer(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Отправляем имя пользователя серверу
            out.println("USERNAME:" + userName);

            // Запускаем поток для чтения сообщений от сервера
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        final String msg = line;
                        SwingUtilities.invokeLater(() -> processServerMessage(msg));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Соединение с сервером потеряно.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Не удалось подключиться к серверу.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // Обработка сообщений от сервера
    private void processServerMessage(String msg) {

        if (msg.startsWith("QUESTION|")) {
            String[] parts = msg.split("\\|");
            if (parts.length >= 6) {
                setMessage(1, parts[1]);
                setMessage(2, parts[2]);
                setMessage(3, parts[3]);
                setMessage(4, parts[4]);
                setMessage(5, parts[5]);
                // Сброс выбора
                bg.clearSelection();
                selectedAnswer = 0;
            }
        } else if (msg.startsWith("LEADERBOARD|")) {
            leaderboardModel.setRowCount(0);
            String data = msg.substring("LEADERBOARD|".length());
            String[] entries = data.split("\\|");
            for (String entry : entries) {
                String[] fields = entry.split(";");
                if (fields.length == 3) {
                    try {
                        int place = Integer.parseInt(fields[0]);
                        String name = fields[1];
                        int score = Integer.parseInt(fields[2]);
                        leaderboardModel.addRow(new Object[]{place, name, score});
                    } catch (NumberFormatException ignored) {}
                }
            }
        } else if (msg.startsWith("ANSWER_RESULT|")) {
            String[] parts = msg.split("\\|");
            if (parts.length >= 3) {
                String result = parts[1];
                String points = parts[2];
                JOptionPane.showMessageDialog(this,
                        result.equalsIgnoreCase("correct") ?
                                "Правильно! Вы получили " + points + " очков." :
                                "Неправильно. Ваши очки: " + points,
                        "Результат ответа",
                        result.equalsIgnoreCase("correct") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Изменение текста в полях
    public void setMessage(int fieldNumber, String message) {
        switch (fieldNumber) {
            case 1: l1.setText(message); break;
            case 2: l2.setText(message); break;
            case 3: l3.setText(message); break;
            case 4: l4.setText(message); break;
            case 5: l5.setText(message); break;
            default: throw new IllegalArgumentException("fieldNumber должен быть от 1 до 5");
        }
        b.setEnabled(true );
    }

    // Обработка нажатия кнопки "Ответить"
    public class ButtonActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            if (selectedAnswer < 1 || selectedAnswer > 4) {
                JOptionPane.showMessageDialog(Reader.this, "Пожалуйста, выберите ответ.", "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Отправляем ответ на сервер в формате: ANSWER|<номер варианта>
            out.println("ANSWER|" + selectedAnswer);
            // Блокируем кнопку, чтобы не отправлять повторно до ответа сервера
            b.setEnabled(false);
        }
    }


}
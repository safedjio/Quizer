import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// Главный класс сервера
public class QuizServer {
    private static final int PORT = 12345;

    // Список всех активных клиентов (для рассылки сообщений)
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    // Менеджер викторины: вопросы, ответы, рейтинг
    private final QuizManager quizManager = new QuizManager();

    // Для хранения ответов текущего раунда
    private final Map<ClientHandler, Integer> currentAnswers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new QuizServer().start();
    }

    public void start() {
        System.out.println("Сервер запущен на порту " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Подключился новый клиент: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Запускает новый раунд: очищает ответы и рассылает вопрос всем клиентам
    public synchronized void startNewQuestionRound() {
        currentAnswers.clear();
        QuizQuestion q = quizManager.getCurrentQuestion();
        String msg = String.format("QUESTION|%s|%s|%s|%s|%s",
                q.getQuestion(),
                q.getOptions()[0],
                q.getOptions()[1],
                q.getOptions()[2],
                q.getOptions()[3]);
        broadcast(msg);
        System.out.println("Новый вопрос отправлен всем клиентам: " + q.getQuestion());
    }

    // Отправить текущий вопрос конкретному клиенту
    public synchronized void sendQuestion(ClientHandler client) {
        QuizQuestion q = quizManager.getCurrentQuestion();
        String msg = String.format("QUESTION|%s|%s|%s|%s|%s",
                q.getQuestion(),
                q.getOptions()[0],
                q.getOptions()[1],
                q.getOptions()[2],
                q.getOptions()[3]);
        client.sendMessage(msg);
    }

    // Обработка ответа от клиента
    public synchronized void handleAnswer(ClientHandler client, int answer) {
        if (currentAnswers.containsKey(client)) {
            // Клиент уже ответил на этот вопрос
            client.sendMessage("ERROR|Вы уже ответили на этот вопрос");
            return;
        }
        currentAnswers.put(client, answer);
        System.out.println("Ответ получен от " + client.getUserName() + ": " + answer);

        // Если все клиенты ответили — обрабатываем ответы
        if (currentAnswers.size() == clients.size()) {
            processAnswers();
            quizManager.nextQuestion();
            startNewQuestionRound();
        }
    }

    // Обработка всех ответов текущего раунда
    private void processAnswers() {
        for (Map.Entry<ClientHandler, Integer> entry : currentAnswers.entrySet()) {
            ClientHandler client = entry.getKey();
            int answer = entry.getValue();
            boolean correct = quizManager.isCorrectAnswer(answer);
            int points = correct ? 100 : 0;
            if (correct) {
                quizManager.addPoints(client.getUserName(), points);
            }
            client.sendMessage("ANSWER_RESULT|" + (correct ? "correct" : "wrong") + "|" + quizManager.getPoints(client.getUserName()));
        }
        broadcastLeaderboard();
    }

    // Удалить клиента из списка (при отключении)
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        currentAnswers.remove(client);
    }

    // Отправить сообщение всем клиентам
    public void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    // Рассылка рейтинга всем клиентам
    public void broadcastLeaderboard() {
        List<LeaderboardEntry> leaderboard = quizManager.getLeaderboard();
        StringBuilder sb = new StringBuilder("LEADERBOARD");
        for (LeaderboardEntry entry : leaderboard) {
            sb.append("|").append(entry.place).append(";").append(entry.name).append(";").append(entry.points);
        }
        broadcast(sb.toString());
    }
}

// Класс для обработки клиента
class ClientHandler implements Runnable {
    private final Socket socket;
    private final QuizServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String userName;

    public ClientHandler(Socket socket, QuizServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUserName() {
        return userName;
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Ожидаем имя пользователя
            String line = in.readLine();
            if (line != null && line.startsWith("USERNAME:")) {
                userName = line.substring("USERNAME:".length()).trim();
                System.out.println("Пользователь вошёл: " + userName);
            } else {
                sendMessage("ERROR|Не указано имя пользователя");
                closeConnection();
                return;
            }

            // Отправляем текущий вопрос сразу после входа
            server.sendQuestion(this);

            // Отправляем текущий рейтинг
            server.broadcastLeaderboard();

            // Обработка сообщений от клиента
            while ((line = in.readLine()) != null) {
                if (line.startsWith("ANSWER|")) {
                    try {
                        int answer = Integer.parseInt(line.substring("ANSWER|".length()));
                        server.handleAnswer(this, answer);
                    } catch (NumberFormatException e) {
                        sendMessage("ERROR|Неверный формат ответа");
                    }
                } else {
                    sendMessage("ERROR|Неизвестная команда");
                }
            }
        } catch (IOException e) {
            System.out.println("Клиент " + userName + " отключился.");
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        server.removeClient(this);
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}

// Класс для управления вопросами и рейтингом
class QuizManager {
    private final List<QuizQuestion> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;

    // Рейтинг: имя -> очки
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public QuizManager() {
        // Пример вопросов
        questions.add(new QuizQuestion("Какой язык программирования используется?", new String[]{"Java", "Python", "C++", "JavaScript"}, 1));
        questions.add(new QuizQuestion("Столица Франции?", new String[]{"Берлин", "Париж", "Лондон", "Мадрид"}, 2));
        questions.add(new QuizQuestion("2 + 2 = ?", new String[]{"3", "4", "5", "6"}, 2));
    }

    public QuizQuestion getCurrentQuestion() {
        return questions.get(currentQuestionIndex);
    }

    public boolean isCorrectAnswer(int answer) {
        QuizQuestion q = getCurrentQuestion();
        return answer == q.getCorrectOption();
    }

    public void nextQuestion() {
        currentQuestionIndex = (currentQuestionIndex + 1) % questions.size();
    }

    public void addPoints(String user, int points) {
        scores.merge(user, points, Integer::sum);
    }

    public int getPoints(String user) {
        return scores.getOrDefault(user, 0);
    }

    public List<LeaderboardEntry> getLeaderboard() {
        List<LeaderboardEntry> list = new ArrayList<>();
        int place = 1;
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> list.add(new LeaderboardEntry(place+1, e.getKey(), e.getValue())));

        return list;
    }
}

// Класс вопроса
class QuizQuestion {
    private final String question;
    private final String[] options;
    private final int correctOption; // 1..4

    public QuizQuestion(String question, String[] options, int correctOption) {
        this.question = question;
        this.options = options;
        this.correctOption = correctOption;
    }

    public String getQuestion() {
        return question;
    }

    public String[] getOptions() {
        return options;
    }

    public int getCorrectOption() {
        return correctOption;
    }
}

// Запись для таблицы лидеров
class LeaderboardEntry {
    public final int place;
    public final String name;
    public final int points;

    public LeaderboardEntry(int place, String name, int points) {
        this.place = place;
        this.name = name;
        this.points = points;
    }
}
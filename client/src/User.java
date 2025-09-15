import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class User {
    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int serverPort = 12345;

        Reader quiz = new Reader("Quiz", serverIp, serverPort);
        quiz.setVisible(true);
        quiz.setResizable(false);
        quiz.setLocationRelativeTo(null);
    }
}

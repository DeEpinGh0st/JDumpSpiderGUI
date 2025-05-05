package cn.wanghw;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GUI extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/cn/wanghw/GUI.fxml"));
        primaryStage.setTitle("JDumpSpiderGUI - Java Heap Analysis Tool");
        primaryStage.setScene(new Scene(root, 1050, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
} 
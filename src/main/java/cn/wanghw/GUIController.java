package cn.wanghw;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.*;

public class GUIController {
    @FXML private MenuItem openMenuItem;
    @FXML private MenuItem aboutMenuItem;
    @FXML private TextField filePathField;
    @FXML private Button selectFileButton;
    @FXML private ComboBox<String> analysisSelector;
    @FXML private Button analyzeButton;
    @FXML private Button exportButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private TableView<KeyValuePair> resultTable;
    @FXML private TableColumn<KeyValuePair, String> categoryColumn;
    @FXML private TableColumn<KeyValuePair, String> keyColumn;
    @FXML private TableColumn<KeyValuePair, String> valueColumn;
    @FXML private Label authorLabel;

    private File selectedFile;
    private Main main;
    private Map<String, List<KeyValuePair>> allResults;

    public static class KeyValuePair {
        private final String key;
        private final String value;
        private final String category;
        public KeyValuePair(String key, String value, String category) {
            this.key = key;
            this.value = value;
            this.category = category;
        }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String getCategory() { return category; }
    }

    @FXML
    public void initialize() {
        main = new Main();
        resultTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        categoryColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCategory()));
        keyColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getKey()));
        valueColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getValue()));
        progressIndicator.setVisible(false);
        statusLabel.setText("就绪");
        authorLabel.setText("Console by whwlsfb  & GUI by S0cke3t");
        setupContextMenu();
    }

    @FXML
    private void handleOpen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择堆转储文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Heap Dump Files", "*.*"));
        selectedFile = fileChooser.showOpenDialog(getStage());
        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
            main.heapfile = selectedFile;
            analyzeButton.setDisable(false);
            statusLabel.setText("就绪");
        }
    }

    @FXML
    private void handleAbout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("JDumpSpider GUI - Java Heap Analysis Tool");
        alert.setContentText("Java堆分析工具\nConsole by whwlsfb\nGUI by S0cke3t");
        alert.showAndWait();
    }

    @FXML
    private void handleSelectFile(ActionEvent event) {
        handleOpen(event);
    }

    @FXML
    private void handleAnalyze(ActionEvent event) {
        analyzeButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("正在分析...");
        resultTable.getItems().clear();
        analysisSelector.getItems().clear();
        Thread analysisThread = new Thread(() -> {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                PrintStream out = new PrintStream(bout);
                main.call(out);
                String result = bout.toString();
                allResults = parseResults(result);
                javafx.application.Platform.runLater(() -> {
                    ObservableList<String> items = FXCollections.observableArrayList("All");
                    items.addAll(allResults.keySet());
                    analysisSelector.setItems(items);
                    analysisSelector.getSelectionModel().selectFirst();
                    updateTable();
                    exportButton.setDisable(false);
                    progressIndicator.setVisible(false);
                    statusLabel.setText("分析完成");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    resultTable.getItems().clear();
                    resultTable.getItems().add(new KeyValuePair("错误", e.getMessage(), "系统"));
                    progressIndicator.setVisible(false);
                    statusLabel.setText("分析失败");
                    analyzeButton.setDisable(false);
                });
            }
        });
        analysisThread.setDaemon(true);
        analysisThread.start();
    }

    @FXML
    private void handleExport(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("导出分析结果");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File exportFile = fileChooser.showSaveDialog(getStage());
        if (exportFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileOutputStream(exportFile))) {
                for (Map.Entry<String, List<KeyValuePair>> entry : allResults.entrySet()) {
                    writer.println("===========================================");
                    writer.println(entry.getKey());
                    writer.println("-------------");
                    for (KeyValuePair pair : entry.getValue()) {
                        writer.println(pair.getKey() + " = " + pair.getValue());
                    }
                    writer.println();
                }
                statusLabel.setText("结果已导出到: " + exportFile.getAbsolutePath());
            } catch (Exception e) {
                statusLabel.setText("导出失败: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleAnalysisSelector(ActionEvent event) {
        updateTable();
    }

    private void updateTable() {
        if (allResults == null) return;
        String selected = analysisSelector.getSelectionModel().getSelectedItem();
        ObservableList<KeyValuePair> items = FXCollections.observableArrayList();
        if ("All".equals(selected)) {
            for (Map.Entry<String, List<KeyValuePair>> entry : allResults.entrySet()) {
                items.addAll(entry.getValue());
            }
        } else {
            List<KeyValuePair> selectedResults = allResults.get(selected);
            if (selectedResults != null) {
                items.addAll(selectedResults);
            }
        }
        resultTable.setItems(items);
    }

    private Map<String, List<KeyValuePair>> parseResults(String result) {
        Map<String, List<KeyValuePair>> analysisResults = new LinkedHashMap<>();
        String[] sections = result.split("===========================================");
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            String[] lines = section.trim().split("\n");
            if (lines.length < 3) continue;
            String title = lines[0].trim();
            List<KeyValuePair> pairs = new ArrayList<>();
            StringBuilder currentKey = new StringBuilder();
            StringBuilder currentValue = new StringBuilder();
            for (int i = 2; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.equals("not found!")) {
                    pairs.add(new KeyValuePair("/", "/", title));
                    break;
                }
                if (line.isEmpty()) continue;
                int equalIndex = line.indexOf(" = ");
                if (equalIndex != -1) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 3).trim();
                    pairs.add(new KeyValuePair(key, value, title));
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    String value = line.substring(1, line.length() - 1).trim();
                    if (!currentKey.toString().isEmpty()) {
                        pairs.add(new KeyValuePair(currentKey.toString(), value, title));
                        currentKey = new StringBuilder();
                    } else {
                        pairs.add(new KeyValuePair("配置项", value, title));
                    }
                } else {
                    if (line.endsWith(":")) {
                        if (!currentKey.toString().isEmpty()) {
                            pairs.add(new KeyValuePair(currentKey.toString(), currentValue.toString(), title));
                        }
                        currentKey = new StringBuilder(line.substring(0, line.length() - 1));
                        currentValue = new StringBuilder();
                    } else {
                        if (currentValue.length() > 0) {
                            currentValue.append("\n");
                        }
                        currentValue.append(line);
                    }
                }
            }
            if (!currentKey.toString().isEmpty() && !currentValue.toString().isEmpty()) {
                pairs.add(new KeyValuePair(currentKey.toString(), currentValue.toString(), title));
            }
            if (!pairs.isEmpty()) {
                analysisResults.put(title, pairs);
            }
        }
        return analysisResults;
    }

    private void setupContextMenu() {
        ContextMenu tableContextMenu = new ContextMenu();
        MenuItem copyRowItem = new MenuItem("复制整行");
        MenuItem copyKeyItem = new MenuItem("复制键");
        MenuItem copyValueItem = new MenuItem("复制值");
        tableContextMenu.getItems().addAll(copyRowItem, copyKeyItem, copyValueItem);
        resultTable.setContextMenu(tableContextMenu);
        copyRowItem.setOnAction(e -> {
            ObservableList<KeyValuePair> selected = resultTable.getSelectionModel().getSelectedItems();
            StringBuilder sb = new StringBuilder();
            for (KeyValuePair kv : selected) {
                sb.append(kv.getCategory()).append("\t").append(kv.getKey()).append("\t").append(kv.getValue()).append("\n");
            }
            copyToClipboard(sb.toString().trim());
        });
        copyKeyItem.setOnAction(e -> {
            ObservableList<KeyValuePair> selected = resultTable.getSelectionModel().getSelectedItems();
            StringBuilder sb = new StringBuilder();
            for (KeyValuePair kv : selected) {
                sb.append(kv.getKey()).append("\n");
            }
            copyToClipboard(sb.toString().trim());
        });
        copyValueItem.setOnAction(e -> {
            ObservableList<KeyValuePair> selected = resultTable.getSelectionModel().getSelectedItems();
            StringBuilder sb = new StringBuilder();
            for (KeyValuePair kv : selected) {
                sb.append(kv.getValue()).append("\n");
            }
            copyToClipboard(sb.toString().trim());
        });
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private Stage getStage() {
        return (Stage) filePathField.getScene().getWindow();
    }
} 
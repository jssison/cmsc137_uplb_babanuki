## How to run JAR file
If errors are encountered, do the following:

```bash
sudo apt update
sudo apt install openjfx

java --module-path /usr/share/openjfx/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar "UPLB Babanuki.jar"
```

Else,
```bash
chmod +x 'UPLB Babanuki.jar'
java -jar 'UPLB Babanuki.jar'
```

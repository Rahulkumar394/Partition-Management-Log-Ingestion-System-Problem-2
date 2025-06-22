Bilkul! Aapka sawaal ekdam a-one hai. Chaliye, ek-ek karke aapki original 'Rules' image ke har point ko test karte hain aur dekhte hain ki woh fix hua hai ya nahi.

Main har rule ke liye testing steps aur **Expected Output** (kaisa result dikhna chahiye) likh raha hoon.

---

### **Final Testing Guide: Sabhi Rules ko Kaise Verify Karein**

Pehle, yeh sunishchit karein ki aapka poora system chal raha hai:
```bash
docker-compose up --build -d
```
System ko 1-2 minute chalne dein taaki data generate ho.

---

#### **Rule 1: Retention Settings for `logs.events` topic**
*   **Goal:** Verify karna ki topic par 1 din (86400000 ms) ki retention policy lagi hai.

*   **Kaise Test Karein:**
    1.  Kafka container ke andar enter karein:
        ```bash
        docker exec -it log-ingestion-system-kafka-1 bash
        ```
    2.  Container ke andar, yeh command chalayein:
        ```bash
        kafka-topics --bootstrap-server localhost:9092 --describe --topic logs.events
        ```

*   **✅ Expected Output:**
    Output ki pehli line mein `Configs: retention.ms=86400000` likha hua dikhega. Yeh is baat ka saboot hai ki rule fix ho gaya hai.
    ```
    Topic: logs.events   TopicId: <some_id>   PartitionCount: 50   ReplicationFactor: 1   Configs: retention.ms=86400000
    ...
    ```
    (Container se bahar aane ke liye `exit` likhein)

---

#### **Rule 2: Partition balance validation (automated)**
*   **Goal:** Verify karna ki logs sabhi 50 partitions mein barabar fail rahe hain aur kisi ek partition par load nahi hai.

*   **Kaise Test Karein:**
    Kisi bhi ek consumer ke logs ko live dekhein. `PartitionMonitoringService` har minute ek summary report print karegi.
    ```bash
    docker logs log-ingestion-system-log-consumer-1-1 -f
    ```

*   **✅ Expected Output:**
    Aapko har minute neeche di gayi line jaisa log dikhega. Agar "active partitions" ka number 50 ke kareeb (jaise 45-50) hai, to iska matlab distribution bilkul sahi hai.
    ```
    INFO 1 --- [   scheduling-1] c.l.s.PartitionMonitoringService : ✅ Consumers have received messages from 48 out of 50 partitions.
    ```
    Yeh saabit karta hai ki aapka system "fair distribution" kar raha hai.

---

#### **Rule 3: Separate consumer group IDs for different roles**
*   **Goal:** Samjhna ki ek hi group ID (`log-consumer-group`) load balancing ke liye sahi kaam kar rahi hai.

*   **Kaise Test Karein:**
    Hum teeno consumers ke logs ko dekhenge aur confirm karenge ki woh alag-alag partitions se kaam le rahe hain.
    1.  Terminal 1 mein: `docker logs log-ingestion-system-log-consumer-1-1 -f`
    2.  Terminal 2 mein: `docker logs log-ingestion-system-log-consumer-2-1 -f`
    3.  Terminal 3 mein: `docker logs log-ingestion-system-log-consumer-3-1 -f`

*   **✅ Expected Output:**
    Aapko har terminal mein alag-alag `Partition:` number dikhenge.
    *   Consumer 1 ke log mein `Partition: 12`, `Partition: 33` dikh sakta hai.
    *   Consumer 2 ke log mein `Partition: 5`, `Partition: 21` dikh sakta hai.
    *   Consumer 3 ke log mein `Partition: 44`, `Partition: 8` dikh sakta hai.

    Yeh dikhata hai ki teeno milkar 50 partitions ka kaam aapas mein baant rahe hain, jo ki load balancing ke liye bilkul sahi hai.

---

#### **Rule 4: Central config for partition count**
*   **Goal:** Verify karna ki partition count (50) centrally configured hai.

*   **Kaise Test Karein:**
    Yeh test **Step 1** mein pehle hi ho chuka hai. `kafka-topics --describe` command ka output yeh confirm karta hai.

*   **✅ Expected Output:**
    Wahi output jo Step 1 mein mila tha: `Topic: logs.events ... PartitionCount: 50 ...`
    Yeh dikhata hai ki `docker-compose.yml` mein set kiya gaya centralized partition count kaam kar raha hai.

---

#### **Rule 5: Kafka JMX Exporter or kafka-exporter container**
*   **Goal:** Verify karna ki Prometheus dono exporters (`kafka-exporter` aur naye `jmx-exporter`) se data le paa raha hai.

*   **Kaise Test Karein:**
    Browser mein Prometheus Targets page kholein: `http://localhost:9090/targets`

*   **✅ Expected Output:**
    Page par aapko **dono** jobs (`kafka-exporter` aur `kafka-jmx`) ka "State" **UP** (hare rang mein) dikhna chahiye.
    *   `kafka-exporter` job ka endpoint `http://kafka-exporter:9308/metrics` hoga.
    *   `kafka-jmx` job ka endpoint `http://jmx-exporter:5556/metrics` hoga.

    Agar dono `UP` hain, to yeh rule 100% fix ho gaya hai.

---

#### **Rule 6: Log rate per app (custom metric)**
*   **Goal:** Verify karna ki har `appName` ke liye alag se custom metric ban raha hai.

*   **Kaise Test Karein:**
    Prometheus mein neeche di gayi query chalayein: `http://localhost:9090/graph`
    ```promql
    sum(rate(kafka_producer_messages_sent_by_app_total[2m])) by (appName)
    ```

*   **✅ Expected Output:**
    Aapko "Table" view mein har `appName` ke liye ek alag entry dikhegi, jaisa aapke pichle screenshot mein dikh raha tha.
    ```
    Element                                              Value
    {appName="app-001"}                                  0.09...
    {appName="app-003"}                                  0.08...
    ... (list of all active apps)
    ```
    Yeh saabit karta hai ki aapka custom metric bilkul sahi kaam kar raha hai.

---

### **Grafana "No Data" Issue ko Kaise Fix aur Verify Karein**

Aapke Grafana screenshot mein "No Data" dikh raha hai kyunki dashboard purani, galat metric names ka istemal kar raha hai.

*   **Kaise Fix Karein:**
    1.  Grafana mein jaayein (`http://localhost:3000`).
    2.  Apne purane "Kafka Log Ingestion Dashboard" ko kholें.
    3.  Top-right mein **Settings (cog wheel ⚙️)** icon par click karein.
    4.  Left side menu mein, **"Delete dashboard"** par click karein aur delete karein.
    5.  Ab, top-left mein **`+` icon par click karein aur "Import"** chunein.
    6.  Mere pichle jawab mein diye gaye **"Corrected Grafana Dashboard JSON"** ko poora copy karein aur "Import via panel json" waale box mein paste karein.
    7.  **"Load"** par click karein.
    8.  Agli screen par, "Prometheus" data source chunein aur **"Import"** par click karein.

*   **✅ Verification:**
    Naya import kiya gaya dashboard khul jaayega aur ab uske sabhi panels (Messages Sent Rate, Failed Rate, Consumer Lag, etc.) mein data dikhna shuru ho jaayega. **"No Data" ab nahi dikhega.**
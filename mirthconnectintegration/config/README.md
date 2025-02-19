# Mirth Connect Installation Configuration

## Configuration Changes

### Update Log4j2 Configuration
Copy the `log4j2.properties` file to the Mirth Connect configuration directory:

```sh
Copy mirthconnectintegration/config/log4j2.properties "C:\Program Files\Mirth Connect\conf\log4j2.properties"
```

This ensures that Mirth Connect logs are properly configured.

### Enable Logger Statements for Java Classes
To view the logger statements printed in Java classes added to `custom-lib`, refer to [`customlib.md`](mirthconnectintegration/config/customlib.md) for instructions on creating the Java flat JAR location.

### Enable Debugging of Channels
Update `-Djava.awt.headless` to `false` for debugging channels. Copy the `mcserver.vmoptions` file:

```sh
Copy mirthconnectintegration/config/mcserver.vmoptions "C:\Program Files\Mirth Connect\mcserver.vmoptions"
```

### Increase Java Heap Memory
Update the heap size to `-Xmx2048m`. Copy the `mcservice.vmoptions` file:

```sh
Copy mirthconnectintegration/config/mcservice.vmoptions "C:\Program Files\Mirth Connect\mcservice.exe"
```

This increases the memory allocation for Mirth Connect services to 2GB, improving performance.

---



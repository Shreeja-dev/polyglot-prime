# Generating Java JARs for Mirth Connect Custom-Lib

Follow these steps to generate the required Java JAR files and copy them into Mirth Connect's `custom-lib` directory, allowing channel scripts to utilize the functions and methods in the JAR.

## Step 1: Get the Latest Code
Clone the latest `main` branch of the `polyglot-prime` repository:

[Polyglot Prime GitHub Repository](https://github.com/tech-by-design/polyglot-prime)

```sh
git clone https://github.com/tech-by-design/polyglot-prime.git
cd polyglot-prime
```

## Step 2: Modify Packaging in `pom.xml`

Update the `pom.xml` in `hub-prime` to use `jar` packaging:

```xml
<groupId>org.techbd</groupId>
<artifactId>hub-prime</artifactId>
<version>currentpomversion</version>
<packaging>jar</packaging>
```

## Step 3: Build the JAR

Navigate to `hub-prime` and run the Maven package command:

```sh
cd hub-prime
mvn clean package -DskipTests
```

This generates the JAR file at:

```
/polyglot-prime/hub-prime/target/hub-prime-0.<currentpomversion>.0.jar
```

## Step 4: Prepare the JAR for Mirth Connect

1. Copy the generated JAR to a folder in WSL and navigate to that directory (referred to as the *current directory* from now on).
2. Extract the JAR file:

   ```sh
   jar xf hub-prime-0.<currentpomversion>.0.jar
   ```

3. Copy the files from `BOOT-INF/classes` to the current directory:

   ```sh
   cp -r BOOT-INF/classes/* ./
   ```

4. Copy files from `BOOT-INF/lib` to the current directory:

   ```sh
   cp -r BOOT-INF/lib/* ./
   ```

5. Recreate a flat JAR:

   ```sh
   jar cf hub-prime-flat.jar *
   ```

6. Verify the contents of the new JAR:

   ```sh
   jar tf hub-prime-flat.jar
   ```

## Step 5: Copy JARs to Mirth Connect

Copy all JARs from the current directory *except* `hub-prime-<currentpomversion>.jar` and `xpp3-1.1.6.jar` to the Mirth Connect custom library folder:

```sh
cp *.jar "C:/Program Files/Mirth Connect/custom-lib"
```

This ensures the required dependencies are available for use in Mirth Connect channel scripts.
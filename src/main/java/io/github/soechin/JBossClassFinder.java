package io.github.soechin;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JBossClassFinder {
  private static final String ALL_CLASSES_FILE = "all-classes.txt";
  private static final String DUPLICATE_CLASSES_FILE = "duplicate-classes.txt";

  public static void main(String[] args) {
    // 獲取控制台
    Console console = System.console();
    if (console == null) {
      System.err.println("無法獲取控制台，請在支援控制台的環境中執行此程式");
      return;
    }

    // 從控制台讀取 jboss 資料夾路徑
    System.out.print("請輸入 jboss/wildfly 資料夾路徑: ");
    String jbossHome = console.readLine().trim();

    Path jbossPath = Paths.get(jbossHome);
    System.out.println("home: " + jbossPath);

    // 獲取 jboss/server/default 資料夾
    Path defaultPath = jbossPath.resolve("server/default");
    if (Files.exists(jbossPath.resolve("lib")) &&
        Files.exists(jbossPath.resolve("deploy"))) {
      defaultPath = jbossPath;
    }

    // 獲取 wildfly/standalone 資料夾
    Path standalonePath = jbossPath.resolve("standalone");
    if (Files.exists(jbossPath.resolve("lib")) &&
        Files.exists(jbossPath.resolve("deployments"))) {
      standalonePath = jbossPath;
    }

    // 如果是 jboss，則掃描 default 資料夾
    if (Files.exists(defaultPath)) {
      System.out.println("default: " + defaultPath);

      Path deployPath = defaultPath.resolve("deploy");
      if (!Files.exists(deployPath)) {
        System.err.println("找不到 deploy 資料夾");
        return;
      }

      System.out.println("deploy: " + deployPath);

      // 列出 deploy 資料夾中的 war 資料夾
      File[] warDirs = deployPath.toFile().listFiles((dir, name) -> {
        return name.toLowerCase().endsWith(".war");
      });

      for (int i = 0; i < warDirs.length; i++) {
        System.out.println("  [" + (i + 1) + "] " + warDirs[i].getName());
      }

      int choice = -1;
      while (choice < 1 || choice > warDirs.length) {
        System.out.print("請選擇 war 資料夾[1-" + warDirs.length + "]: ");
        try {
          choice = Integer.parseInt(console.readLine().trim());
        } catch (NumberFormatException e) {
          System.err.println("請輸入有效的數字");
        }
      }

      Path warPath = warDirs[choice - 1].toPath();
      System.out.println("war: " + warPath);

      scanWarPath(defaultPath, warPath);
    }
    // 如果是 wildfly，則掃描 standalone 資料夾
    else if (Files.exists(standalonePath)) {
      System.out.println("standalone: " + standalonePath);

      // 獲取 deployments 資料夾
      Path deploymentsPath = standalonePath.resolve("deployments");
      if (!Files.exists(deploymentsPath)) {
        System.err.println("找不到 deployments 資料夾");
        return;
      }

      System.out.println("deployments: " + deploymentsPath);

      // 列出 deployments 資料夾中的 war 資料夾
      File[] warDirs = deploymentsPath.toFile().listFiles((dir, name) -> {
        return name.toLowerCase().endsWith(".war");
      });

      for (int i = 0; i < warDirs.length; i++) {
        System.out.println("  [" + (i + 1) + "] " + warDirs[i].getName());
      }

      int choice = -1;
      while (choice < 1 || choice > warDirs.length) {
        System.out.print("請選擇 war 資料夾[1-" + warDirs.length + "]: ");
        try {
          choice = Integer.parseInt(console.readLine().trim());
        } catch (NumberFormatException e) {
          System.err.println("請輸入有效的數字");
        }
      }

      Path warPath = warDirs[choice - 1].toPath();
      System.out.println("war: " + warPath);

      scanWarPath(standalonePath, warPath);
    }
    // 如果都不是，則輸出錯誤訊息
    else {
      System.err.println("找不到 jboss/server/default 或 wildfly/standalone 資料夾");
      return;
    }
  }

  /**
   * 掃描 standalone/lib 及 WEB-INF/lib 資料夾中的 jar 檔案，並輸出到檔案
   * 
   * @param standalonePath default 或 standalone 資料夾路徑
   * @param warPath        war 資料夾路徑
   */
  private static void scanWarPath(Path standalonePath, Path warPath) {
    // 列出 standalone/lib 及 WEB-INF/lib 資料夾中的 jar 檔案
    List<File> jarFiles = new ArrayList<>();
    Path standaloneLibPath = standalonePath.resolve("lib");
    Path webinfLibPath = warPath.resolve("WEB-INF/lib");

    if (Files.exists(standaloneLibPath)) {
      File[] files = standaloneLibPath.toFile().listFiles((dir, name) -> {
        return name.toLowerCase().endsWith(".jar");
      });
      if (files != null) {
        jarFiles.addAll(Arrays.asList(files));
      }
    }

    if (Files.exists(webinfLibPath)) {
      File[] files = webinfLibPath.toFile().listFiles((dir, name) -> {
        return name.toLowerCase().endsWith(".jar");
      });
      if (files != null) {
        jarFiles.addAll(Arrays.asList(files));
      }
    }

    // 掃描 jar 裡的 class 並輸出到檔案
    Map<String, Set<File>> classLocations = new HashMap<>();

    try (PrintWriter writer = new PrintWriter(new File(ALL_CLASSES_FILE))) {
      for (File jar : jarFiles) {
        Path relativePath = standalonePath.relativize(jar.toPath());
        writer.println(relativePath);
        System.out.println("  - " + relativePath);

        String[] classFiles = scanJarFile(jar);
        for (String classFile : classFiles) {
          classLocations.computeIfAbsent(classFile, k -> new HashSet<>()).add(jar);
          writer.println("  - " + classFile);
        }

        writer.println();
      }

      System.out.println("已輸出到 " + ALL_CLASSES_FILE + " 檔案");
    } catch (IOException e) {
      System.err.println("輸出到檔案時發生錯誤: " + e.getMessage());
    }

    // 輸出重複的 class 到檔案
    int count = 0;

    try (PrintWriter writer = new PrintWriter(new File(DUPLICATE_CLASSES_FILE))) {
      for (Map.Entry<String, Set<File>> entry : classLocations.entrySet()) {
        if (entry.getValue().size() > 1) {
          writer.println(entry.getKey());

          for (File jar : entry.getValue()) {
            Path relativePath = standalonePath.relativize(jar.toPath());
            writer.println("  - " + relativePath);
          }

          writer.println();
          count++;
        }
      }

      System.out.println("找到 " + count + " 個重複的 class");
      System.out.println("已輸出到 " + DUPLICATE_CLASSES_FILE + " 檔案");
    } catch (IOException e) {
      System.err.println("輸出到檔案時發生錯誤: " + e.getMessage());
    }
  }

  /**
   * 掃描 jar 檔案中的 class 檔案
   * 
   * @param jarFile jar 檔案
   * @return class 檔案列表
   */
  private static String[] scanJarFile(File jarFile) {
    List<String> classFiles = new ArrayList<>();
    try (JarFile jar = new JarFile(jarFile)) {
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          classFiles.add(entry.getName());
        }
      }
    } catch (IOException e) {
      System.err.println("掃描 jar 檔案時發生錯誤: " + e.getMessage());
    }
    return classFiles.toArray(new String[0]);
  }

}

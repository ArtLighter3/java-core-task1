import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TestLogParser {

    enum Status { PASSED, FAILED, SKIPPED }

    record TestResult(String name, Status status, long durationMs) {

        //переопределены, потому что иначе тесты с одним именем, но разными статусами или длительностью будут
        //считаться разными.
        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) return false;
            TestResult that = (TestResult) object;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    static class TestResultInvalidSyntaxException extends Exception {
        private final String testResultLine;

        public TestResultInvalidSyntaxException(String testResultLine, String message) {
            super(message);
            this.testResultLine = testResultLine;
        }

        public String getTestResultLine() {
            return testResultLine;
        }
    }

    private static List<TestResultInvalidSyntaxException> errors = new ArrayList<>();

    public static List<TestResult> parse(List<String> lines) {
        errors.clear();
        //equals и hashCode не используются напрямую, т.к. это происходит при сравнивании внутри хэш-коллекции
        Set<TestResult> results = new LinkedHashSet<>();

        for (String line : lines) {
            try {
                TestResult result = parseLine(line);

                results.remove(result);
                results.add(result);
            } catch (TestResultInvalidSyntaxException ex) {
                errors.add(ex);
            }
        }

        return results.stream().toList();
    }

    private static TestResult parseLine(String line) throws TestResultInvalidSyntaxException {
        String[] parts = line.split("\\|");
        if (parts.length < 3) {
            throw new TestResultInvalidSyntaxException(line, "нет разделителя");
        }

        String name = parts[0];

        Status status;
        try {
            status = Status.valueOf(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new TestResultInvalidSyntaxException(line, String.format("неверный статус '%s'", parts[1]));
        }

        long durationMs;
        try {
            durationMs = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new TestResultInvalidSyntaxException(line, "длительность должна быть числом");
        }
        if (durationMs < 0) {
            throw new TestResultInvalidSyntaxException(line, "длительность не может быть отрицательной");
        }

        return new TestResult(name, status, durationMs);
    }

    public static void main(String[] args) {
        String logFilePath = "run.log";

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(logFilePath))) {
            while (br.ready()) {
                lines.add(br.readLine());
            }
        } catch (IOException ex) {
            System.out.printf("Ошибка чтения файла '%s'%n", logFilePath);
        }

        List<TestResult> results = parse(lines);

        System.out.println("Результаты: ");
        for (TestResult result : results) {
            System.out.printf("  %s - %s (%d ms)%n", result.name, result.status, result.durationMs);
        }

        System.out.print("Битые строки: ");
        if (!errors.isEmpty()) {
            System.out.println();
            for (TestResultInvalidSyntaxException ex : errors) {
                System.out.printf("  %s - %s%n", ex.getTestResultLine(), ex.getMessage());
            }
        } else System.out.println("Битых строк нет");

        Optional<TestResult> firstFailed = firstFailed(results);
        System.out.printf("Первый упавший: %s", firstFailed.isPresent() ? firstFailed.get().name : "Упадений нет");

    }

    private static Optional<TestResult> firstFailed(List<TestResult> results) {
        return results
                .stream()
                .filter((result) -> result.status == Status.FAILED)
                .findFirst();
    }

}
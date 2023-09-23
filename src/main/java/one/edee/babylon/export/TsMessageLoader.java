package one.edee.babylon.export;

import one.edee.babylon.export.ts.ECMAScript6BaseListener;
import one.edee.babylon.export.ts.ECMAScript6Lexer;
import one.edee.babylon.export.ts.ECMAScript6Parser;
import one.edee.babylon.msgfile.TranslationFileUtils;
import one.edee.babylon.util.FileUtils;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class TsMessageLoader implements MessageLoader {

    public static final String TS_FILE_EXTENSION = ".ts";

    @Override
    public boolean canBeLoaded(String filePath) {
        return filePath.endsWith(TS_FILE_EXTENSION);
    }

    @Override
    public Map<String, String> loadPrimaryMessages(String filePath) {
        return ofNullable(loadFile(filePath))
                .map(ECMAScript6BaseListener::getPropertyDefinitions)
                .orElse(Collections.emptyMap());
    }

    @Override
    public Map<String, Map<String, String>> loadTranslations(String filePath, List<String> languages) {
        return languages
                .stream()
                .map(lang ->
                        new AbstractMap.SimpleEntry<>(
                                lang,
                                loadTranslations(filePath, lang)
                        )
                ).collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue)
                );
    }


    public static Map<String, String> dumpTsFile(Reader reader) throws IOException {
        return readTsFile(reader).getPropertyDefinitions();
    }

    public static ECMAScript6BaseListener readTsFile(Reader reader) throws IOException {
        CharStream input = CharStreams.fromReader(reader);

        ECMAScript6Lexer lexer = new ECMAScript6Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ECMAScript6Parser parser = new ECMAScript6Parser(tokens);
        ParseTree tree = parser.program();

        ECMAScript6BaseListener listener = new ECMAScript6BaseListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);

        return listener;
    }

    private Map<String, String> loadTranslations(String filePath, String language) {
        String translationFilePath = TranslationFileUtils.getFileNameForTranslation(filePath, language);
        return ofNullable(loadFile(translationFilePath))
                .map(ECMAScript6BaseListener::getPropertyDefinitions)
                .orElse(Collections.emptyMap());
    }


    public static ECMAScript6BaseListener loadFile(String filePath) {
        if (FileUtils.exists(filePath)) {

            try (Reader inputStreamReader = new FileReader(fileFromPath(filePath))) {
                return readTsFile(inputStreamReader);
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        } else {
            return null;
        }
    }


    private static File fileFromPath(String path) {
        return FileUtils.fileFromPathOrThrow(path);
    }

}

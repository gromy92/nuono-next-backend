package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.nio.file.Path;

public interface FileParseInputExtractor {

    boolean supports(FileParseTaskInputRow input);

    FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) throws IOException;
}

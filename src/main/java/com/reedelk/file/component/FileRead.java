package com.reedelk.file.component;

import com.reedelk.file.internal.attribute.FileAttribute;
import com.reedelk.file.internal.exception.NotValidFileException;
import com.reedelk.file.internal.read.*;
import com.reedelk.runtime.api.annotation.*;
import com.reedelk.runtime.api.commons.MimeTypeUtils;
import com.reedelk.runtime.api.component.ProcessorSync;
import com.reedelk.runtime.api.flow.FlowContext;
import com.reedelk.runtime.api.message.Message;
import com.reedelk.runtime.api.message.MessageBuilder;
import com.reedelk.runtime.api.message.content.MimeType;
import com.reedelk.runtime.api.script.ScriptEngineService;
import com.reedelk.runtime.api.script.dynamicvalue.DynamicString;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.reedelk.file.internal.commons.Messages.FileRead.FILE_NAME_ERROR;
import static com.reedelk.runtime.api.commons.StringUtils.isBlank;

@ModuleComponent("File Read")
@ComponentOutput(
        attributes = FileAttribute.class,
        payload = byte[].class,
        description = "The content of the file read from the file system from the given path and file name.")
@ComponentInput(
        payload = Object.class,
        description = "The input payload is used to evaluate the file name to read.")
@Description("Reads a file from the file system from the given file name and optionally provided base path. " +
                "The file read strategy determines if the file should be streamed from the file system or " +
                "loaded into memory before continuing with the execution of the flow. " +
                "The component can also be configured to acquire a lock before reading the file.")
@Component(service = FileRead.class, scope = ServiceScope.PROTOTYPE)
public class FileRead implements ProcessorSync {

    @Property("File name")
    @Hint("/var/logs/log1.txt")
    @Example("/var/logs/log1.txt")
    @Description("The path and name of the file to be read from the file system.")
    private DynamicString fileName;

    @Property("Base path")
    @Hint("/var/logs")
    @Example("/var/logs")
    @Description("Optional base path from which files with the given <i>File name</i> will be read from. " +
            "The final file will be read from <i>Base Path</i> + <i>File Name</i>.")
    private String basePath;

    @Property("Read mode")
    @Example("STREAM")
    @InitValue("DEFAULT")
    @DefaultValue("DEFAULT")
    @Description("Determines the read strategy. When <i>Default</i> the file is completely read into memory. " +
            "When <i>Stream</i> the file is read only on demand only when the message payload is being consumed. " +
            "This is the preferred method to read large files from the filesystem.")
    private ReadMode mode;

    @Property("Auto mime type")
    @Example("false")
    @InitValue("true")
    @DefaultValue("false")
    @Description("If true, the mime type of the payload is determined from the extension of the file read.")
    private boolean autoMimeType;

    @Property("Mime type")
    @MimeTypeCombo
    @Example(MimeType.AsString.TEXT_XML)
    @DefaultValue(MimeType.AsString.APPLICATION_BINARY)
    @When(propertyName = "autoMimeType", propertyValue = "false")
    @When(propertyName = "autoMimeType", propertyValue = When.BLANK)
    @Description("The mime type of the file read from the filesystem.")
    private String mimeType;

    @Property("Configuration")
    @Group("Configuration")
    private FileReadConfiguration configuration;

    @Reference
    private ScriptEngineService service;

    private ReadStrategy strategy;

    @Override
    public void initialize() {
        strategy = ReadMode.STREAM.equals(mode) ?
                new ReadStrategyStream() :
                new ReadStrategyDefault();
    }

    @Override
    public Message apply(FlowContext flowContext, Message message) {

        Optional<String> evaluated = service.evaluate(fileName, flowContext, message);

        return evaluated.map(filePath -> {

            MimeType actualMimeType = MimeTypeUtils.fromFileExtensionOrParse(autoMimeType, filePath, mimeType, MimeType.APPLICATION_BINARY);

            Path path = isBlank(basePath) ? Paths.get(filePath) : Paths.get(basePath, filePath);

            ReadConfigurationDecorator config = new ReadConfigurationDecorator(configuration);

            FileAttribute attributes = new FileAttribute(path.toString());

            MessageBuilder messageBuilder = MessageBuilder.get(FileRead.class);

            strategy.read(path, config, messageBuilder, actualMimeType);

            return messageBuilder
                    .attributes(attributes)
                    .build();

        }).orElseThrow(() -> new NotValidFileException(FILE_NAME_ERROR.format(fileName.toString())));
    }

    public void setConfiguration(FileReadConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setAutoMimeType(boolean autoMimeType) {
        this.autoMimeType = autoMimeType;
    }

    public void setFileName(DynamicString fileName) {
        this.fileName = fileName;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setMode(ReadMode mode) {
        this.mode = mode;
    }
}

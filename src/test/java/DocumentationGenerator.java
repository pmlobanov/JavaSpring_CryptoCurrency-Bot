import org.junit.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import spbstu.mcs.telegramBot.Application;

public class DocumentationGenerator {


        ApplicationModules modules = ApplicationModules.of(Application.class);

        @Test
        public void writeDocumentationSnippets() {

            new Documenter(modules)
                    .writeModulesAsPlantUml()
                    .writeIndividualModulesAsPlantUml()
                    .writeModuleCanvases(Documenter.CanvasOptions.defaults())
                    .writeDocumentation()
                    .writeAggregatingDocument();
        }
    }


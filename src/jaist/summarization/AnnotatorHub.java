package jaist.summarization;

import edu.stanford.nlp.pipeline.EntityMentionsAnnotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * Created by chientran on 9/29/15.
 */
public class AnnotatorHub {
    private static AnnotatorHub instance = null;

    private StanfordCoreNLP pipeline = null;
    private EntityMentionsAnnotator entityMentionsAnnotator = null;

    protected AnnotatorHub(){
        Properties props = new Properties();
        try {
            String propFilename = "resources/config.properties";

            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFilename);

            if (inputStream != null) {
                props.load(inputStream);
            }
        }catch(Exception e){
            System.out.println("Error while loading properties file, using default properties");
            props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref, stopword");
        }

        this.pipeline = new StanfordCoreNLP(props);

        this.entityMentionsAnnotator = new EntityMentionsAnnotator("entitymentions", new Properties());
    }

    public static AnnotatorHub getInstance(){
        if (instance == null){
            instance = new AnnotatorHub();
        }

        return instance;
    }

    public StanfordCoreNLP getPipeline(){
        return this.pipeline;
    }

    public EntityMentionsAnnotator getEntityMentionsAnnotator(){
        return this.entityMentionsAnnotator;
    }

}

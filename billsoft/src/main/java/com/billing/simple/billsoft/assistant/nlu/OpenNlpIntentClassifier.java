package com.billing.simple.billsoft.assistant.nlu;

import jakarta.annotation.PostConstruct;
import opennlp.tools.doccat.*;
import opennlp.tools.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Pure-JVM OpenNLP MaxEnt Intent & Capability Classifier.
 * Operates 100% offline, loading or training a DocumentCategorizer model.
 */
@Component
public class OpenNlpIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenNlpIntentClassifier.class);

    private DoccatModel model;
    private DocumentCategorizerME categorizer;

    public static class ClassificationResult {
        private final String topCategory;
        private final double confidence;
        private final Map<String, Double> allProbabilities;

        public ClassificationResult(String topCategory, double confidence, Map<String, Double> allProbabilities) {
            this.topCategory = topCategory;
            this.confidence = confidence;
            this.allProbabilities = allProbabilities;
        }

        public String getTopCategory() { return topCategory; }
        public double getConfidence() { return confidence; }
        public Map<String, Double> getAllProbabilities() { return allProbabilities; }
    }

    @PostConstruct
    public synchronized void init() {
        try {
            ClassPathResource modelResource = new ClassPathResource("models/rupeecrm-intents.bin");
            if (modelResource.exists()) {
                try (InputStream is = modelResource.getInputStream()) {
                    this.model = new DoccatModel(is);
                    this.categorizer = new DocumentCategorizerME(this.model);
                    log.info("Loaded OpenNLP intent model from classpath: models/rupeecrm-intents.bin");
                    return;
                }
            }

            // Train dynamically from corpus
            log.info("Model rupeecrm-intents.bin not found on classpath. Training from corpus...");
            trainFromCorpusResource("corpus/rupeecrm_training_corpus.txt");
        } catch (Exception e) {
            log.error("Failed to initialize OpenNLP Intent Classifier. Fallback mode will be active.", e);
        }
    }

    public synchronized void trainFromCorpusResource(String corpusPath) throws IOException {
        ClassPathResource corpusResource = new ClassPathResource(corpusPath);
        if (!corpusResource.exists()) {
            throw new FileNotFoundException("Training corpus resource not found: " + corpusPath);
        }

        List<DocumentSample> samples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(corpusResource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int spaceIdx = trimmed.indexOf(' ');
                if (spaceIdx > 0) {
                    String category = trimmed.substring(0, spaceIdx).trim();
                    String sampleText = trimmed.substring(spaceIdx + 1).trim();
                    String[] tokens = tokenize(sampleText);
                    if (tokens.length > 0 && !category.isEmpty()) {
                        samples.add(new DocumentSample(category, tokens));
                    }
                }
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalStateException("Corpus has zero valid training samples: " + corpusPath);
        }

        ObjectStream<DocumentSample> sampleStream = new CollectionObjectStream<>(samples);
        TrainingParameters params = TrainingParameters.defaultParams();
        params.put(TrainingParameters.ITERATIONS_PARAM, 100);
        params.put(TrainingParameters.CUTOFF_PARAM, 1);
        params.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");

        DoccatFactory factory = new DoccatFactory(new FeatureGenerator[]{
                new BagOfWordsFeatureGenerator(),
                new NGramFeatureGenerator(2, 3)
        });

        this.model = DocumentCategorizerME.train("en", sampleStream, params, factory);
        this.categorizer = new DocumentCategorizerME(this.model);
        log.info("Successfully trained OpenNLP MaxEnt intent classifier model from {} samples.", samples.size());
    }

    public ClassificationResult classify(String rawText) {
        if (categorizer == null || rawText == null || rawText.isBlank()) {
            return new ClassificationResult("UNKNOWN_FALLBACK", 0.0, Collections.emptyMap());
        }

        String[] tokens = tokenize(rawText);
        double[] outcomes = categorizer.categorize(tokens);
        String bestCategory = categorizer.getBestCategory(outcomes);

        int bestIndex = categorizer.getIndex(bestCategory);
        double confidence = bestIndex >= 0 && bestIndex < outcomes.length ? outcomes[bestIndex] : 0.0;

        Map<String, Double> probabilities = new HashMap<>();
        for (int i = 0; i < categorizer.getNumberOfCategories(); i++) {
            probabilities.put(categorizer.getCategory(i), outcomes[i]);
        }

        return new ClassificationResult(bestCategory, confidence, probabilities);
    }

    private String[] tokenize(String text) {
        String normalized = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\u0900-\\u097F\\s]", " ").trim();
        if (normalized.isEmpty()) return new String[]{"empty"};
        return normalized.split("\\s+");
    }
}

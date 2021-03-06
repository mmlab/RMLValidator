package be.ugent.mmlab.rml.extractor;

/**
 * *************************************************************************
 *
 * RML : RML Mapping Factory abstract class
 *
 * Factory responsible of RML Mapping generation.
 *
 * based on R2RMLMappingFactory in db2triples
 * 
 * modified by andimou
 *
 ***************************************************************************
 */


import static be.ugent.mmlab.rml.extractor.RMLUnValidatedMappingExtractor.extractValuesFromResource;
import be.ugent.mmlab.rml.model.GraphMap;
import be.ugent.mmlab.rml.model.LogicalSource;
import be.ugent.mmlab.rml.model.ObjectMap;
import be.ugent.mmlab.rml.model.PredicateMap;
import be.ugent.mmlab.rml.model.PredicateObjectMap;
import be.ugent.mmlab.rml.model.RMLMapping;
import be.ugent.mmlab.rml.model.ReferencingObjectMap;
import be.ugent.mmlab.rml.model.std.StdLogicalSource;
import be.ugent.mmlab.rml.model.std.StdObjectMap;
import be.ugent.mmlab.rml.model.std.StdSubjectMap;
import be.ugent.mmlab.rml.model.SubjectMap;
import be.ugent.mmlab.rml.model.TriplesMap;
import be.ugent.mmlab.rml.model.reference.ReferenceIdentifier;
import be.ugent.mmlab.rml.model.std.StdPredicateMap;
import be.ugent.mmlab.rml.model.std.StdPredicateObjectMap;
import be.ugent.mmlab.rml.sesame.RMLSesameDataSet;
import be.ugent.mmlab.rml.rmlvalidator.RMLValidator;
import be.ugent.mmlab.rml.rml.RMLVocabulary;
import be.ugent.mmlab.rml.rml.RMLVocabulary.R2RMLTerm;
import be.ugent.mmlab.rml.rmlvalidator.RMLMappingValidator;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;

public class RMLValidatedMappingExtractor extends RMLUnValidatedMappingExtractor
    implements RMLMappingExtractor {

    public static RMLMapping extractRMLMapping(String homeandimouDesktopofferrmlttl, boolean b) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    RMLMappingValidator validator;
    RMLUnValidatedMappingExtractor subextractor;

    // Log
    private static final Logger log = LogManager.getLogger(RMLValidatedMappingExtractor.class);   

    public RMLValidatedMappingExtractor(RMLMappingValidator validator) {
        this.validator = validator;
        this.subextractor = new RMLUnValidatedMappingExtractor();
       // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public void RMLValidatedMappingExtractor(RMLMappingExtractor extractor, RMLValidator validator){
//        setRMLMappingValidator(validator);
    }
    
    /**
     *
     * @param validator
     */
    public void RMLValidatedMappingExtractor(RMLValidator validator){
        this.validator = validator;
    }
    
    @Override
    public PredicateObjectMap extractPredicateObjectMap(
            RMLSesameDataSet rmlMappingGraph,
            Resource triplesMapSubject,
            Resource predicateObject,
            Set<GraphMap> savedGraphMaps,
            Map<Resource, TriplesMap> triplesMapResources,
            TriplesMap triplesMap) {

        List<Statement> statements = getStatements(
                rmlMappingGraph, triplesMapSubject, RMLVocabulary.R2RML_NAMESPACE,
                RMLVocabulary.R2RMLTerm.PREDICATE_OBJECT_MAP, triplesMap);

        validator.checkStatements(predicateObject, statements, R2RMLTerm.PREDICATE_OBJECT_MAP);

        //AD:Normally the following is not needed
        //validator.checkStatements(predicateObject, statements, R2RMLTerm.PREDICATE);

        Set<PredicateMap> predicateMaps = new HashSet<PredicateMap>();
        for (Statement statement : statements) {

            PredicateMap predicateMap = extractPredicateMap(
                    rmlMappingGraph, (Resource) statement.getObject(),
                    savedGraphMaps, triplesMap);

            predicateMaps.add(predicateMap);
        }

        // Extract object maps
        URI o = rmlMappingGraph.URIref(RMLVocabulary.R2RML_NAMESPACE
                + RMLVocabulary.R2RMLTerm.OBJECT_MAP);
        statements = rmlMappingGraph.tuplePattern(predicateObject, o, null);
        validator.checkStatements(predicateObject, statements, R2RMLTerm.OBJECT_MAP);

        Set<ObjectMap> objectMaps = new HashSet<ObjectMap>();
        Set<ReferencingObjectMap> refObjectMaps = new HashSet<ReferencingObjectMap>();

        for (Statement statement : statements) {
            log.debug(
                    Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                    + "Try to extract object map..");
            ReferencingObjectMap refObjectMap = extractReferencingObjectMap(
                    rmlMappingGraph, (Resource) statement.getObject(),
                    savedGraphMaps, triplesMapResources, triplesMap);
            if (refObjectMap != null) {
                refObjectMaps.add(refObjectMap);
                // Not a simple object map, skip to next.
                continue;
            } else {
                validator.checkStatements(predicateObject, statements, R2RMLTerm.REF_OBJECT_MAP_CLASS);
            }
            //TODO:validator for refObjectMaps
            ObjectMap objectMap = extractObjectMap(rmlMappingGraph,
                    (Resource) statement.getObject(), savedGraphMaps, triplesMap);

            objectMap.setOwnTriplesMap(triplesMapResources.get(triplesMapSubject));
            log.debug(
                    Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                    + "ownTriplesMap attempted "
                    + triplesMapResources.get(statement.getContext())
                    + " for object " + statement.getObject().stringValue());
            objectMaps.add(objectMap);
        }

        PredicateObjectMap predicateObjectMap = new StdPredicateObjectMap(
                predicateMaps, objectMaps, refObjectMaps);

        // Add graphMaps
        Set<GraphMap> graphMaps = new HashSet<GraphMap>();
        Set<Value> graphMapValues = extractValuesFromResource(
                rmlMappingGraph, predicateObject, RMLVocabulary.R2RMLTerm.GRAPH_MAP);

        if (graphMapValues != null) {
            graphMaps = extractGraphMapValues(
                    rmlMappingGraph, graphMapValues, savedGraphMaps, triplesMap);
            log.info(
                    Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                    + "graph Maps returned " + graphMaps);
        }

        predicateObjectMap.setGraphMaps(graphMaps);
        log.debug(
                Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                + "Extract predicate-object map done.");
        return predicateObjectMap;
    }
    
    @Override
    public PredicateMap extractPredicateMap(
            RMLSesameDataSet rmlMappingGraph, Resource object,
            Set<GraphMap> graphMaps, TriplesMap triplesMap) {

        // Extract object maps properties
        Value constantValue = extractValueFromTermMap(rmlMappingGraph,
                object, RMLVocabulary.R2RMLTerm.CONSTANT, triplesMap);
        String stringTemplate = extractLiteralFromTermMap(rmlMappingGraph,
                object, RMLVocabulary.R2RMLTerm.TEMPLATE, triplesMap);
        URI termType = (URI) extractValueFromTermMap(rmlMappingGraph, object,
                RMLVocabulary.R2RMLTerm.TERM_TYPE, triplesMap);

        String inverseExpression = extractLiteralFromTermMap(rmlMappingGraph,
                object, RMLVocabulary.R2RMLTerm.INVERSE_EXPRESSION, triplesMap);

        //MVS: Decide on ReferenceIdentifier
        ReferenceIdentifier referenceValue = 
                extractReferenceIdentifier(rmlMappingGraph, object, triplesMap);
        
        validator.checkTermMap(
                constantValue, stringTemplate, referenceValue, object.stringValue(), R2RMLTerm.PREDICATE_MAP);

        PredicateMap result = new StdPredicateMap(null, constantValue,
                stringTemplate, inverseExpression, referenceValue, termType);
        log.debug(
                Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                + "Extract predicate map done.");
        return result;
    }
 
    //AD:change it to re-use the one from RMLUnValidatedMappingExtractor
    @Override
    public ObjectMap extractObjectMap(RMLSesameDataSet rmlMappingGraph,
            Resource object, Set<GraphMap> graphMaps, TriplesMap triplesMap){
        
        // Extract object maps properties
        Value constantValue = extractValueFromTermMap(rmlMappingGraph,
                object, R2RMLTerm.CONSTANT, triplesMap);
        String stringTemplate = extractLiteralFromTermMap(rmlMappingGraph,
                object, R2RMLTerm.TEMPLATE, triplesMap);
        String languageTag = extractLiteralFromTermMap(rmlMappingGraph,
                object, R2RMLTerm.LANGUAGE, triplesMap);
        URI termType = (URI) extractValueFromTermMap(rmlMappingGraph, object,
                R2RMLTerm.TERM_TYPE, triplesMap);
        URI dataType = (URI) extractValueFromTermMap(rmlMappingGraph, object,
                R2RMLTerm.DATATYPE, triplesMap);
        String inverseExpression = extractLiteralFromTermMap(rmlMappingGraph,
                object, R2RMLTerm.INVERSE_EXPRESSION, triplesMap);

        //MVS: Decide on ReferenceIdentifier
        ReferenceIdentifier referenceValue = 
                extractReferenceIdentifier(rmlMappingGraph, object, triplesMap);
        
        validator.checkTermMap(
                constantValue, stringTemplate, referenceValue, object.stringValue(), R2RMLTerm.OBJECT_MAP);

        StdObjectMap result = new StdObjectMap(null, constantValue, dataType,
                languageTag, stringTemplate, termType, inverseExpression,
                referenceValue);
        log.debug(
                Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                //"[RMLMappingFactory:extractObjectMap] 
                + "Extract object map done.");
        return result;
    }

    /**
     * Extract subjectMap contents
     *
     * @param rmlMappingGraph
     * @param triplesMapSubject
     * @return
     */
    //AD:change it to reuse the one from RMLUnValidatedMappingExtractor
    @Override
    public SubjectMap extractSubjectMap(
            RMLSesameDataSet rmlMappingGraph, Resource triplesMapSubject,
            Set<GraphMap> savedGraphMaps, TriplesMap triplesMap) {
        log.debug(
                Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                + "Extract subject map...");

        // Extract subject map
        URI p = rmlMappingGraph.URIref(RMLVocabulary.R2RML_NAMESPACE
                + R2RMLTerm.SUBJECT_MAP);
        List<Statement> statements = rmlMappingGraph.tuplePattern(
                triplesMapSubject, p, null);

        validator.checkStatements(triplesMapSubject, statements, R2RMLTerm.SUBJECT_MAP);
        if (!statements.isEmpty()) {
            Resource subjectMap = (Resource) statements.get(0).getObject();

            Value constantValue = extractValueFromTermMap(rmlMappingGraph,
                    subjectMap, R2RMLTerm.CONSTANT, triplesMap);
            String stringTemplate = extractLiteralFromTermMap(rmlMappingGraph,
                    subjectMap, R2RMLTerm.TEMPLATE, triplesMap);
            URI termType = (URI) extractValueFromTermMap(rmlMappingGraph,
                    subjectMap, R2RMLTerm.TERM_TYPE, triplesMap);
            String inverseExpression = extractLiteralFromTermMap(rmlMappingGraph,
                    subjectMap, R2RMLTerm.INVERSE_EXPRESSION, triplesMap);

            validator.checkTermMap(
                    constantValue, stringTemplate, null, subjectMap.toString(), R2RMLTerm.SUBJECT_MAP);

            //MVS: Decide on ReferenceIdentifier
            //TODO:Add check if the referenceValue is a valid reference according to the reference formulation
            ReferenceIdentifier referenceValue =
                    extractReferenceIdentifier(rmlMappingGraph, subjectMap, triplesMap);
            //AD: The values of the rr:class property must be IRIs. 
            //AD: Would that mean that it can not be a reference to an extract of the input or a template?
            Set<URI> classIRIs = extractURIsFromTermMap(rmlMappingGraph,
                    subjectMap, R2RMLTerm.CLASS);

            Set<GraphMap> graphMaps = new HashSet<GraphMap>();
            Set<Value> graphMapValues = extractValuesFromResource(
                    rmlMappingGraph, subjectMap, R2RMLTerm.GRAPH_MAP);

            if (graphMapValues != null) {
                graphMaps = extractGraphMapValues(
                        rmlMappingGraph, graphMapValues, savedGraphMaps, triplesMap);
                log.info(
                        Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                        + "graph Maps returned " + graphMaps);
            }
            SubjectMap result = new StdSubjectMap(triplesMap, constantValue,
                    stringTemplate, termType, inverseExpression, referenceValue,
                    classIRIs, graphMaps);
            log.debug(
                    Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                    + "Subject map extracted.");
            return result;
        }
        else 
            return null;
    }
   
   
    
    private Set<GraphMap> extractGraphMapValues(
            RMLSesameDataSet rmlMappingGraph, Set<Value> graphMapValues,
            Set<GraphMap> savedGraphMaps, TriplesMap triplesMap){
            //throws InvalidR2RMLStructureException {

        Set<GraphMap> graphMaps = new HashSet<GraphMap>();

        for (Value graphMap : graphMapValues) {
            // Create associated graphMap if it has not already created
            boolean found = false;
            GraphMap graphMapFound = null;

            if (found) {
                graphMaps.add(graphMapFound);
            } else {
                GraphMap newGraphMap = null;
                newGraphMap = extractGraphMap(rmlMappingGraph, (Resource) graphMap, triplesMap);

                savedGraphMaps.add(newGraphMap);
                graphMaps.add(newGraphMap);
            }
        }

        return graphMaps;
    }
    
    /**
     *
     * @param rmlMappingGraph
     * @param termType
     * @param term
     * @param triplesMap
     * @return
     */
    @Override
    protected Value extractValueFromTermMap(
            RMLSesameDataSet rmlMappingGraph, Resource termType,
            Enum term, TriplesMap triplesMap) {
        Value value = subextractor.extractValueFromTermMap(
            rmlMappingGraph, termType, term, triplesMap) ;
        //AD:fix this
        //validator.checkMultipleStatements(triplesMap, statements, p, type);
        return value;
    }
   
    @Override
    protected LogicalSource extractLogicalSources(
            RMLSesameDataSet rmlMappingGraph, Resource triplesMapSubject, TriplesMap triplesMap) {

        Resource blankLogicalSource = 
                extractLogicalSource(rmlMappingGraph, triplesMapSubject, triplesMap);
        
        RMLVocabulary.QLTerm referenceFormulation =
                getReferenceFormulation(rmlMappingGraph, triplesMapSubject, blankLogicalSource, triplesMap);

        //Extract the iterator to create the iterator. Some formats have null, like CSV or SQL
        List<Statement> iterators = getStatements(
                rmlMappingGraph, blankLogicalSource,
                RMLVocabulary.RML_NAMESPACE, RMLVocabulary.RMLTerm.ITERATOR, triplesMap);

        validator.checkIterator(blankLogicalSource, iterators, referenceFormulation);

        List<Statement> sourceStatements = getStatements(
                rmlMappingGraph,blankLogicalSource,
                RMLVocabulary.RML_NAMESPACE, RMLVocabulary.RMLTerm.SOURCE, triplesMap);
        
        validator.checkSource(blankLogicalSource, sourceStatements);

        LogicalSource logicalSource = null;

        if (!sourceStatements.isEmpty()) {
            //Extract the file identifier
            String file = sourceStatements.get(0).getObject().stringValue();
            
            if(!iterators.isEmpty())
                logicalSource = 
                        new StdLogicalSource(iterators.get(0).getObject().stringValue(), 
                        file, referenceFormulation);
        }
        
        log.debug(
                Thread.currentThread().getStackTrace()[1].getMethodName() + ": "
                + "Logical source extracted : "
                + logicalSource);
        return logicalSource;
    }
    
    @Override
    protected Resource extractLogicalSource(
            RMLSesameDataSet rmlMappingGraph, Resource triplesMapSubject, TriplesMap triplesMap) {
        
        List<Statement> logicalSourceStatements = getStatements(
                rmlMappingGraph, triplesMapSubject,
                RMLVocabulary.RML_NAMESPACE, RMLVocabulary.RMLTerm.LOGICAL_SOURCE, triplesMap);

        Resource blankLogicalSource = 
                subextractor.extractLogicalSource(rmlMappingGraph, triplesMapSubject, triplesMap);
        
        validator.checkLogicalSource(triplesMapSubject, logicalSourceStatements, triplesMap);
        
        return blankLogicalSource;
    }
    
    /**
     *
     * @param rmlMappingGraph
     * @param triplesMapSubject
     * @param namespace
     * @param term
     * @param triplesMap
     * @return
     */
    @Override
        protected List<Statement> getStatements(
            RMLSesameDataSet rmlMappingGraph, Resource triplesMapSubject, 
            String namespace, RMLVocabulary.Term term, TriplesMap triplesMap){
        
        List<Statement> statements = 
                subextractor.getStatements(
                rmlMappingGraph, triplesMapSubject, namespace, term, triplesMap);

        validator.checkEmptyStatements(
                triplesMap, statements, rmlMappingGraph.URIref(namespace + term), triplesMapSubject);
        //validator.checkMultipleStatements(
        //        triplesMap, statements, rmlMappingGraph.URIref(namespace + term), namespace);
        
        return statements;
    }
    
    /**
     *
     * @param rmlMappingGraph
     * @param term
     * @param termType
     * @param triplesMap
     * @return
     */
    @Override
        protected List<Statement> getStatements(
            RMLSesameDataSet rmlMappingGraph, Enum term,  Resource termType, TriplesMap triplesMap){
        URI p = getTermURI(rmlMappingGraph, term);

        List<Statement> statements = rmlMappingGraph.tuplePattern(termType,
                p, null);
        
        String type = new Object(){}.getClass().getEnclosingMethod().getReturnType().getSimpleName();
        
        //validator.checkMultipleStatements(triplesMap, statements, p, type);
        
        return statements;
    }
    
    /**
     *
     * @param rmlMappingGraph
     * @param triplesMapSubject
     * @param subject
     * @param triplesMap
     * @return
     */
    @Override
    protected RMLVocabulary.QLTerm getReferenceFormulation(
            RMLSesameDataSet rmlMappingGraph, Resource triplesMapSubject, 
            Resource subject, TriplesMap triplesMap) 
    {       
        List<Statement> statements = getStatements(
                rmlMappingGraph, subject, 
                RMLVocabulary.RML_NAMESPACE, RMLVocabulary.RMLTerm.REFERENCE_FORMULATION, triplesMap);
        
        validator.checkReferenceFormulation(subject, statements);
        
        if (statements.isEmpty()) 
            return null;
        else
            return RMLVocabulary.getQLTerms(statements.get(0).getObject().stringValue());
    }
    
    public static boolean isLocalFile(String source) {
        try {
            new URL(source);
            return false;
        } catch (MalformedURLException e) {
            return true;
        }
    }

    private static void extractLogicalTable() {
        // TODO: Original R2RML Logic move here
    }
}

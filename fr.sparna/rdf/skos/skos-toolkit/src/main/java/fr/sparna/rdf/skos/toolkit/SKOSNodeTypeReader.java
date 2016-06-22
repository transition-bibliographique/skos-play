package fr.sparna.rdf.skos.toolkit;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.openrdf.model.Resource;
import org.openrdf.model.Value;
import org.openrdf.query.TupleQueryResultHandlerException;
import org.openrdf.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.sparna.commons.lang.Function;
import fr.sparna.commons.lang.Lists;
import fr.sparna.rdf.sesame.toolkit.query.Perform;
import fr.sparna.rdf.sesame.toolkit.query.SparqlPerformException;
import fr.sparna.rdf.sesame.toolkit.query.SparqlQuery;
import fr.sparna.rdf.sesame.toolkit.util.PropertyReader;
import fr.sparna.rdf.skos.toolkit.SKOSTreeNode.NodeType;

/**
 * Determines if an entry in the tree corresponds to a Concept, a Collection or a ConceptScheme.
 * 
 * @author Thomas Francart
 */
public class SKOSNodeTypeReader {

	private Logger log = LoggerFactory.getLogger(this.getClass().getName());
	
	protected PropertyReader typeReader;
	protected Repository repository;
	
	public SKOSNodeTypeReader(PropertyReader typeReader, Repository repository) {
		super();
		this.typeReader = typeReader;
		this.repository = repository;
	}

	public NodeType readNodeType(java.net.URI node) 
	throws SparqlPerformException {
		List<Value> types = typeReader.read(node);

		for (Value value : types) {
			if(value.stringValue().equals(SKOS.CONCEPT)) {
				return NodeType.CONCEPT;
			} else if(value.stringValue().equals(SKOS.COLLECTION)) {
				
				// determine if the Collection corresponds to a ThesaurusArray or a MT
				final List<String> broaders = new ArrayList<String>();
				Perform.on(repository).select(new GetBroadersOfMembersOfCollection(URI.create(node.toString())) {
					@Override
					protected void handleBroaderOfMemberOfCollection(Resource concept) throws TupleQueryResultHandlerException {
						broaders.add(concept.stringValue());
					}					
				});
				
				// either they have a single parent...
				if(
						broaders.size() == 1
				) {
					return NodeType.COLLECTION_AS_ARRAY;
				} else {
					// or no parent at all... which can mean 2 things...
					if(broaders.size() == 0) {
						// if no broaders were found, test if the collection actually has only concepts as members
						// and not colletions, like Domains in the UNESCO thesaurus
						if(Perform.on(repository).ask(new SparqlQuery(new HasOnlyConceptMembersQuery(node.toString()).getSPARQL()))) {
							// then we consider it a top-level ThesaurusArray
							return NodeType.COLLECTION_AS_ARRAY;
						} else {
							// otherwise, it is a simple collection
							return NodeType.COLLECTION;
						}
					}
					return NodeType.COLLECTION;
				}				
				
			} else if(value.stringValue().equals(SKOS.CONCEPT_SCHEME)) {
				return NodeType.CONCEPT_SCHEME;
			}
		}
		
		log.warn("Unable to determine NodeType for node "+node.toString()+". Node has types : "+Lists.transform(types, new Function<Value, String>() {
			public String apply(Value v) { return v.toString(); }
		}));
		return NodeType.UNKNOWN;
	}
	
}

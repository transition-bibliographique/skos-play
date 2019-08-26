package fr.sparna.rdf.skos.xls2skos;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColumnHeaderParser {
	
	private Logger log = LoggerFactory.getLogger(this.getClass().getName());
	
	private PrefixManager prefixManager;

	public ColumnHeaderParser(PrefixManager prefixManager) {
		super();
		this.prefixManager = prefixManager;
	}
	
	public ColumnHeader parse(String value) {
		return parse(value, (short)-1);
	}

	public ColumnHeader parse(String value, short columnIndex) {
		if(value == null || value.equals("")) {
			return null;
		}
		ColumnHeader h = new ColumnHeader(value);
		h.setDeclaredProperty(parseDeclaredProperty(value));
		h.setProperty(parseProperty(h.getDeclaredProperty()));
		h.setLanguage(parseLanguage(value));
		h.setDatatype(parseDatatype(value));
		h.setInverse(parseInverse(value));
		Map<String, String> parameters = parseParameters(value);
		h.setParameters(parameters);
		// prevent problems if parameter parsing went wrong, if cell content
		// contains parentheses but these are not parameters
		if(parameters != null && parameters.containsKey(ColumnHeader.PARAMETER_ID)) {
			h.setId(parameters.get(ColumnHeader.PARAMETER_ID));
		}
		
		// sets the reconcileProperty from parameters, if needed
		if(parameters.containsKey(ColumnHeader.PARAMETER_RECONCILE_PROPERTY)) {
			IRI reconcileProperty = parseProperty(parameters.get(ColumnHeader.PARAMETER_RECONCILE_PROPERTY));
			if(reconcileProperty == null) {
				 throw new InvalidParameterException("Unable to parse reconcileProperty value : '"+parameters.get(ColumnHeader.PARAMETER_RECONCILE_PROPERTY)+"'");
			}
			h.setReconcileProperty(reconcileProperty);
		}
		
		h.setColumnIndex(columnIndex);
		
		return h;
	}
	
	private IRI parseProperty(String declaredProperty) {
		try {
			if(this.prefixManager.usesKnownPrefix(declaredProperty)) {
				return SimpleValueFactory.getInstance().createIRI(this.prefixManager.uri(declaredProperty, false));
			} else {
				return SimpleValueFactory.getInstance().createIRI(declaredProperty);
			}
		} catch (Exception e) {
			return null;
		}		
	}
	
	private String parseDeclaredProperty(String value) {
		String property = value;
		
		// remove inverse mark
		if(parseInverse(value)) {
			property = value.substring(1);
		}
		
		if(property.contains("@")) {
			property = property.substring(0, property.lastIndexOf('@')).trim();
		}
		if(property.contains("^^")) {
			property = property.substring(0, property.lastIndexOf("^^")).trim();
		}
		if(property.contains("(")) {
			property = property.substring(0, property.lastIndexOf("(")).trim();
		}
		
		return property;		
	}
	
	private Optional<String> parseLanguage(String value) {
		if(value.contains("@")) {
			String language = value.substring(value.lastIndexOf('@')+1);
			// remove the parameters part
			if(language.contains("(")) {
				language = language.substring(0, language.lastIndexOf("("));
			}
			return Optional.of(language.trim());
		}
		return Optional.empty();
	}
	
	private Optional<IRI> parseDatatype(String value) {
		if(value.contains("^^")) {
			String dt = value.substring(value.lastIndexOf("^^")+2);
			// remove the parameters part
			if(dt.contains("(")) {
				dt = dt.substring(0, dt.lastIndexOf("(")).trim();
			}
			
			if(this.prefixManager.usesKnownPrefix(dt)) {
				return Optional.of(SimpleValueFactory.getInstance().createIRI(this.prefixManager.uri(dt, false)));
			} else if (dt.startsWith("<http")){
				return Optional.of(SimpleValueFactory.getInstance().createIRI(dt.substring(1, dt.length()-1)));
			} else {
				return Optional.of(SimpleValueFactory.getInstance().createIRI(dt));
			}
		}
		return Optional.empty();
	}
	
	private Map<String, String> parseParameters(String value) {
		Map<String, String> parameters = new HashMap<>();
		
		try {
			if(value.contains("(") && value.trim().charAt(value.length()-1) == ')') {
				String parametersString = value.substring(value.indexOf("(")+1, value.length()-1);
				// ISSUE : cannot split on "," since this prevents to declare "," as a separator
				// TODO : have a full grammar to be able to really split values.
				String[] splittedParameters = parametersString.split(" ");
				Arrays.stream(splittedParameters).forEach(p -> {
					String[] keyValue;
					// split on column if the separator value is an equal
					if(p.contains("\"=\"")) {
						keyValue = p.split(":");
					} else {
						keyValue = p.split("=");
					}
					String rawKey = keyValue[0];
					String rawValue = keyValue[1];
					
					// parse the key
					String paramKey = rawKey.trim();
					if(paramKey.startsWith("\"") && paramKey.endsWith("\"")) {
						paramKey = paramKey.substring(1, paramKey.length()-1);
					}
					
					// parse the value
					String paramValue = rawValue.trim();
					if(paramValue.startsWith("\"") && paramValue.endsWith("\"")) {
						paramValue = paramValue.substring(1, paramValue.length()-1);
					}
					
					// register the parameter
					parameters.put(paramKey, paramValue);
				});
			}
		} catch (Exception e) {
			log.error("Unsuccessfull parsing of header parameters for header '"+value+"', Exception is "+e.getClass().getName()+" : "+e.getMessage());
			return null;
		} 
		
		return parameters;
	}
	
	private boolean parseInverse(String value) {
		return value.startsWith("^");
	}
	
}

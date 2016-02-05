package org.metadatacenter.terminology.bioportal.serializers.toBioPortal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.metadatacenter.terminology.bioportal.domainObjects2.custom.OntologyClass;

import java.io.IOException;

public class OntologyClassSerializerold extends JsonSerializer<OntologyClass>
{
  @Override public void serialize(OntologyClass ontologyClass, JsonGenerator jsonGenerator,
    SerializerProvider serializerProvider) throws IOException
  {

    jsonGenerator.writeStartObject();

    jsonGenerator.writeStringField("label", ontologyClass.getLabel());
    jsonGenerator.writeStringField("creator", ontologyClass.getCreator());
    jsonGenerator.writeStringField("ontology", ontologyClass.getOntology());

    jsonGenerator.writeArrayFieldStart("definitions");
    for (String definition : ontologyClass.getDefinitions()) {
      jsonGenerator.writeString(definition);
    }
    jsonGenerator.writeEndArray();

    jsonGenerator.writeArrayFieldStart("synonyms");
    for (String synonym : ontologyClass.getSynonyms()) {
      jsonGenerator.writeString(synonym);
    }
    jsonGenerator.writeEndArray();

    jsonGenerator.writeObjectField("relations", ontologyClass.getRelations());

    jsonGenerator.writeEndObject();
  }
}

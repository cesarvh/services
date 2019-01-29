package org.collectionspace.services.batch.nuxeo;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.net.URISyntaxException;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
import org.dom4j.DocumentException;
import org.collectionspace.services.client.PoxPayloadOut;

import org.dom4j.DocumentException;

public class UpdateObjectNationalitiesFromPersonBatchJob extends AbstractBatchJob {
    
    public UpdateObjectNationalitiesFromPersonBatchJob() {
		this.setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_GROUP));
    }
    
    @Override
    public void run() {
        setCompletionStatus(STATUS_MIN_PROGRESS);
        setCompletionStatus(STATUS_COMPLETE);

    }

    public InvocationResults updateNationalitiesFromPerson(String personCsid, Map<String, List> nationalitiesToUpdate) throws URISyntaxException, DocumentException, Exception {
        String sourceField = "collectionobjects_bampfa:bampfaObjectProductionPerson";
        String serviceName = "personauthorities";

        List<String> collectionObjectsList =  findReferencingCollectionObjects(serviceName, personCsid, sourceField);

        int numAffected = 0;

        // These are the CSIDs of all the documents that we need to update
        for (String csid : collectionObjectsList) {
            PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(csid);
            // String inAuthority = getFieldValue(collectionObjectPayload, "collectionobjects_common", "inAuthority");

            // Why is this a String??
            // collectionObjectPayload.getPart("collectionobjects_bampfa").asElement().selectNodes("nationalities/nationality").getText()
            List<String> oldNationalities = getFieldValues(collectionObjectPayload, "collectionobjects_bampfa", "nationalities/nationality");
            try {
            updateNationalities(oldNationalities, nationalitiesToUpdate, csid);
            } catch (Exception e) {

            }

        }

        // These are the CSIDs of all the documents that we need to update

        // getFieldValue






        return null;
    }

    public void updateNationalities(List<String> collectionObjNationalities, Map<String, List> nationalitiesToUpdate, String objCsid) throws URISyntaxException {
        List nationalitiesToAdd = nationalitiesToUpdate.get("add");
        List nationalitiesToDelete = nationalitiesToUpdate.get("delete");

        collectionObjNationalities.addAll(nationalitiesToAdd);
        collectionObjNationalities.removeAll(nationalitiesToDelete);

        // So now collectionObjNationalities is the most updated version of what we need to have. Time to assemble them
        String nationalitiesString = "";
        for (String n : collectionObjNationalities) {
            nationalitiesString += "<nationality>" + n + "</nationality>\n";
        }

        
        String nationalitiesGroupString  = "<nationalities>" + 
                                    nationalitiesString + 
                                    "</nationalities>";


        String updatePayload = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<document name=\"collectionobjects\">" +
            "<ns2:collectionobjects_bampfa xmlns:ns2=\"http://collectionspace.org/services/collectionobject/local/bampfa\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            nationalitiesGroupString + 
            "</ns2:collectionobjects_bampfa>" +
            "</document>";

        // Now update????
    
		AuthorityResource<?, ?> resource = (AuthorityResource<?, ?>) getResourceMap().get("personauthorities");
		// resource.updateAuthorityItem(getServiceContext(), getResourceMap(), createUriInfo(), authorityCsid, objCsid, , updatePayload);
		resource.updateAuthorityItem(getServiceContext(), getResourceMap(), createUriInfo(), objCsid, objCsid, updatePayload);


    }
}

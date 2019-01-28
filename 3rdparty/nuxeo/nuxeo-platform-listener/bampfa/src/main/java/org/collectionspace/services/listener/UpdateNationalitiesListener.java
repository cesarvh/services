package org.collectionspace.services.listener.bampfa;

import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Map;
import java.util.*;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.collectionspace.services.client.workflow.WorkflowClient;
import org.collectionspace.services.common.api.Tools;
import org.collectionspace.services.movement.nuxeo.MovementConstants;
import org.collectionspace.services.nuxeo.util.NuxeoUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.collectionspace.services.batch.BatchResource;
import org.collectionspace.services.batch.nuxeo.UpdateObjectNationalitiesFromPersonBatchJob;
import org.collectionspace.services.batch.nuxeo.UpdateObjectNationalitiesFromPersonBatchJob.updateNationalitiesFromPerson;
import org.collectionspace.services.common.invocable.InvocationResults;


public class UpdateNationalitiesListener implements EventListener {

    private final static Log logger = LogFactory.getLog(UpdateNationalitiesListener.class);
    private final static String NO_FURTHER_PROCESSING_MESSAGE =
            "This event listener will not continue processing this event ...";

    private final static String COLLECTIONOBJECT_DOCTYPE = "CollectionObject";
    protected final static String COLLECTIONOBJECTS_COMMON_SCHEMA = "collectionobjects_common"; // FIXME: Get from external constant
    private final static String COLLECTIONOBJECTS_BAMPFA_SCHEMA = "collectionobjects_bampfa";

    private final static String PERSON_DOCTYPE = "Person";
    private final static String PERSONS_SCHEMA = "persons_common";

    private final static String NONVERSIONED_NONPROXY_DOCUMENT_WHERE_CLAUSE_FRAGMENT =
            "AND ecm:isCheckedInVersion = 0"
            + "AND ecm:isProxy = 0 ";
    private final static String ACTIVE_DOCUMENT_WHERE_CLAUSE_FRAGMENT =
            "AND (ecm:currentLifeCycleState <> 'deleted') "
            + NONVERSIONED_NONPROXY_DOCUMENT_WHERE_CLAUSE_FRAGMENT;


    public static final String PREVIOUS_NATIONALITIES_PROPERTY_NAME = "UpdateNationalitiesListener.prevousNationalities";



    @Override
    public void handleEvent(Event event) throws ClientException {
        logger.trace("In handleEvent in Update Nationalities");

        EventContext eventContext = event.getContext();
        if (eventContext == null || !(eventContext instanceof DocumentEventContext)) {
            return;
        }

        // Check if the relationship is between a collection object and and a persons record
        DocumentEventContext docEventContext = (DocumentEventContext) eventContext;
        DocumentModel docModel = docEventContext.getSourceDocument();
        String docType = docModel.getType();

        if (docType.startsWith("Person") &&
					!docType.startsWith("Personauthority") &&
					!docModel.isVersion() &&
					!docModel.isProxy() &&
					!docModel.getCurrentLifeCycleState().equals(WorkflowClient.WORKFLOWSTATE_DELETED)) {

            if (event.getName().equals(DocumentEventTypes.DOCUMENT_CREATED)) {
                // If the document has just beeen created, it will definitely not be used by any collection object record
                return;
            } else if (event.getName().equals(DocumentEventTypes.BEFORE_DOC_UPDATE)) {
                // Store the previous nationalities list to get them on Document Made thing

                DocumentModel previousDoc = (DocumentModel) docEventContext.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
                ArrayList previousNationalities = (ArrayList) previousDoc.getProperty(PERSONS_SCHEMA, "nationalities");
                docEventContext.setProperty(PREVIOUS_NATIONALITIES_PROPERTY_NAME, previousNationalities);
            } else {
                boolean updateRequired = false;

                List previousNationalities = (List) docEventContext.getProperty(PREVIOUS_NATIONALITIES_PROPERTY_NAME);
                List newNationalities = (List) docModel.getProperty(PERSONS_SCHEMA, "nationalities");
                List newNationalitiesCopy = newNationalities;
                

                if (previousNationalities.size() == 0 || newNationalities.size() == 0) {
                    // There weren't any nationalities then, and none now, so stop here
                    return;
                }

                Collections.sort(previousNationalities);
                Collections.sort(newNationalitiesCopy);

                // if they are equal, we don't need to update the lists
                if (newNationalities.equals(previousNationalities)) {
                    return;
                }
                
                try { updateCollectionObjectsFromPerson(docModel); }
                catch (Exception e) {
                    int x = 0;
                }
            }

        } else if (documentMatchesType(docModel, COLLECTIONOBJECT_DOCTYPE)) {

            if (event.getName().equals("documentCreated")) {
                // Trigger the before thingy
                docModel.getCoreSession().saveDocument(docModel);

                return;
            }

            // Obtain the previous document

            DocumentModel previousDoc = (DocumentModel) docEventContext.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);

            CoreSession coreSession = docEventContext.getCoreSession();

            List<String> nationalities = getNationalities(docModel, coreSession);

            docModel.setProperty(COLLECTIONOBJECTS_BAMPFA_SCHEMA, "nationalities", nationalities);

            return;
        } else {
            logger.trace("No persons or collection object record was involved.");
            return;
        }
    }

    public List<String> getNationalities(DocumentModel docModel, CoreSession coreSession) {
        String fieldRequested = "bampfaObjectProductionPersonGroupList";
        List<Map<String, Object>> bampfaObjectProductionPersonGroupList = (List<Map<String, Object>>) docModel.getProperty(COLLECTIONOBJECTS_BAMPFA_SCHEMA, fieldRequested);

        List<String> allNationalities = new ArrayList<String>();

        for (Map<String, Object> bampfaObjectProductionGroup : bampfaObjectProductionPersonGroupList) {
            String currRefName = (String) bampfaObjectProductionGroup.get("bampfaObjectProductionPerson");

            String query = "SELECT * FROM Person WHERE persons_common:refName=\"" + currRefName + '"';

            List<String> nationalities = (List) coreSession.query(query).get(0).getProperty(PERSONS_SCHEMA, "nationalities");


            for (Object n : nationalities) {
                String nationality = (String) n;
                if (!allNationalities.contains(nationality)) {
                    allNationalities.add(nationality);
                }
            }
        }

        return allNationalities;

    }

    protected static boolean documentMatchesType(DocumentModel docModel, String docType) {
        if (docModel == null || Tools.isBlank(docType)) {
            return false;
        }
        if (docModel.getType().startsWith(docType)) {
            return true;
        }
        return false;
    }
    
    private void updateCollectionObjectsFromPerson(DocumentModel docModel) throws Exception {
        String refName = (String) docModel.getProperty(PERSONS_SCHEMA, "refName");
                        //  docModel.getProperty(PERSONS_SCHEMA, "nationalities")
        String personCsid = (String) docModel.getName();


        UpdateObjectNationalitiesFromPersonBatchJob updater = new UpdateObjectNationalitiesFromPersonBatchJob();


        InvocationResults res = updater.updateNationalitiesFromPerson(personCsid); // throws exception???????
    }

}
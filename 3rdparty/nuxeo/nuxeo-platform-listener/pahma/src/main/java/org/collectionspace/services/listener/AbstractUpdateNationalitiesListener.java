package org.collectionspace.services.listener;

import java.util.GregorianCalendar;
import java.util.HashSet;
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
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

public class AbstractUpdateNationalitiesListener implements EventListener {

    private final static Log logger = LogFactory.getLog(AbstractUpdateNationalitiesListener.class);
    private final static String NO_FURTHER_PROCESSING_MESSAGE =
            "This event listener will not continue processing this event ...";
    
    private final static GregorianCalendar EARLIEST_COMPARISON_DATE = new GregorianCalendar(1600, 1, 1);
    private final static String RELATIONS_COMMON_SCHEMA = "relations_common"; // FIXME: Get from external constant
    private final static String RELATION_DOCTYPE = "Relation"; // FIXME: Get from external constant
    private final static String PERSON_DOCTYPE = "Persons";
    private final static String SUBJECT_CSID_PROPERTY = "subjectCsid"; // FIXME: Get from external constant
    private final static String OBJECT_CSID_PROPERTY = "objectCsid"; // FIXME: Get from external constant
    private final static String SUBJECT_DOCTYPE_PROPERTY = "subjectDocumentType"; // FIXME: Get from external constant
    private final static String OBJECT_DOCTYPE_PROPERTY = "objectDocumentType"; // FIXME: Get from external constant
    protected final static String COLLECTIONOBJECTS_COMMON_SCHEMA = "collectionobjects_common"; // FIXME: Get from external constant
    private final static String COLLECTIONOBJECT_DOCTYPE = "CollectionObject"; // FIXME: Get from external constant
    private final static String COLLECTIONOBJECTS_PAHMA_SCHEMA = "collectionobjects_pahma";
    private final static String PERSONS_SCHEMA = "persons_common";
    private final static String PERSONS_NATIONALITIES_SCHEMA = "persons_common_nationalities";
    private final static String MOVEMENT_DOCTYPE = MovementConstants.NUXEO_DOCTYPE;
    protected final static String COLLECTIONSPACE_CORE_SCHEMA = "collectionspace_core"; // FIXME: Get from external constant
    protected final static String CREATED_AT_PROPERTY = "createdAt"; // FIXME: Get from external constant
    protected final static String UPDATED_AT_PROPERTY = "updatedAt"; // FIXME: Get from external constant
    private final static String NONVERSIONED_NONPROXY_DOCUMENT_WHERE_CLAUSE_FRAGMENT =
            "AND ecm:isCheckedInVersion = 0"
            + "AND ecm:isProxy = 0 ";
    private final static String ACTIVE_DOCUMENT_WHERE_CLAUSE_FRAGMENT =
            "AND (ecm:currentLifeCycleState <> 'deleted') "
            + NONVERSIONED_NONPROXY_DOCUMENT_WHERE_CLAUSE_FRAGMENT;

    public enum EventNotificationDocumentType {
        // Document type about which we've received a notification

        PERSONS, RELATION, COLLECTIONOBJECT;
    }
    
    
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

        String personCsid = "";
        Enum notificationDocummentType;

        if (documentMatchesType(docModel, RELATION_DOCTYPE)) {
            logger.trace("Relation document was received"); // TO DO: add more detail
            // get csid

            personCsid = getCsidForDesiredDocTypeFromRelation(docModel, PERSON_DOCTYPE, COLLECTIONOBJECT_DOCTYPE);

            if (Tools.isBlank(personCsid)) {
                logger.warn("Could not obtain csid for persons record from document event"); // TO DO: More detail
                logger.warn(NO_FURTHER_PROCESSING_MESSAGE);
                return;
            }
            notificationDocummentType = EventNotificationDocumentType.RELATION;
        } else if (documentMatchesType(docModel, PERSON_DOCTYPE)) {
            // get directly from persons record
            logger.trace("Something involving persons record"); // TO DO: More detail

            personCsid = NuxeoUtils.getCsid(docModel);
            if (Tools.isBlank(personCsid)) {
                logger.warn("Could not obtain CSID for Person record from document event."); 
                logger.warn(NO_FURTHER_PROCESSING_MESSAGE);
                return;
            }
            notificationDocummentType = EventNotificationDocumentType.PERSONS;
        } else {
            logger.trace("No persons record was involved");
        }
    }







    /**
     * Returns the CSID for a desired document type from a Relation record,
     * where the relationship involves two specified, different document types.
     *
     * @param relationDocModel a document model for a Relation record.
     * @param desiredDocType a desired document type.
     * @param relatedDocType a related document type.
     * @throws ClientException
     * @return the CSID from the desired document type in the relation. Returns
     * an empty string if the Relation record does not involve both the desired
     * and related document types, or if the desired document type is at both
     * ends of the relation.
     */
    protected static String getCsidForDesiredDocTypeFromRelation(DocumentModel relationDocModel,
            String desiredDocType, String relatedDocType) throws ClientException {
        String csid = "";
        String subjectDocType = (String) relationDocModel.getProperty(RELATIONS_COMMON_SCHEMA, SUBJECT_DOCTYPE_PROPERTY);
        String objectDocType = (String) relationDocModel.getProperty(RELATIONS_COMMON_SCHEMA, OBJECT_DOCTYPE_PROPERTY);
        if (subjectDocType.startsWith(desiredDocType) && objectDocType.startsWith(desiredDocType)) {
            return csid;
        }
        if (subjectDocType.startsWith(desiredDocType) && objectDocType.startsWith(relatedDocType)) {
            csid = (String) relationDocModel.getProperty(RELATIONS_COMMON_SCHEMA, SUBJECT_CSID_PROPERTY);
        } else if (subjectDocType.startsWith(relatedDocType) && objectDocType.startsWith(desiredDocType)) {
            csid = (String) relationDocModel.getProperty(RELATIONS_COMMON_SCHEMA, OBJECT_CSID_PROPERTY);
        }
        return csid;
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
    

}
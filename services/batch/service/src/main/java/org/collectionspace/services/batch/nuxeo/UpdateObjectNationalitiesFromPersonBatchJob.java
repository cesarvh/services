package org.collectionspace.services.batch.nuxeo;

import java.util.List;
import java.util.Arrays;
import java.net.URISyntaxException;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.vocabulary.AuthorityResource;
import org.dom4j.DocumentException;

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



    public InvocationResults updateNationalitiesFromPerson(String csid) throws URISyntaxException, DocumentException, Exception {
        String nationalitiesField = "nationalities";
        String serviceName = "personauthorities";

        List<String> collectionObjectsList =  findReferencingCollectionObjects(serviceName, csid, nationalitiesField);

        return null;
    }
}

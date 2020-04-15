package org.collectionspace.services.batch.nuxeo;

import java.util.Arrays;
import java.util.HashMap;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.collectionspace.services.common.invocable.InvocationContext.ListCSIDs;
import org.collectionspace.services.common.invocable.InvocationContext;
import org.collectionspace.services.common.invocable.InvocationContext.Params.Param;
import org.collectionspace.services.common.invocable.InvocationResults;
import org.collectionspace.services.common.ResourceMap;
import org.collectionspace.services.common.NuxeoBasedResource;

import org.collectionspace.services.client.CollectionObjectClient;
import org.collectionspace.services.client.PayloadOutputPart;
import org.collectionspace.services.client.PoxPayloadOut;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

public class BulkObjectEditBatchJob extends  AbstractBatchJob {
  final Logger logger = LoggerFactory.getLogger(BulkObjectEditBatchJob.class);

  public BulkObjectEditBatchJob() {
    setSupportedInvocationModes(Arrays.asList(INVOCATION_MODE_LIST));
  }

  @Override
  public void run() {
    setCompletionStatus(STATUS_MIN_PROGRESS);
    try {
      InvocationContext ctx = getInvocationContext();

      String mode = ctx.getMode();


      if (mode.equalsIgnoreCase(INVOCATION_MODE_LIST)) {
        ArrayList<String> csids  = new ArrayList<String>();
        csids.addAll(ctx.getListCSIDs().getCsid());
        HashMap<String, String>  fieldsToValues = this.getValues();
        InvocationResults results = new InvocationResults();

        

        if (fieldsToValues.isEmpty()) {
          throw new Exception("There is nothing to update. Aborting...");
        }
        
        // setResults(updateRecords(csids, fieldsToValues));
        int numAffected = 0 ;
        String payload = preparePayload(fieldsToValues);
        // PoxPayloadOut batchPayload;
        
        // PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(collectionObjectCsid);

        for (String csid : csids) {
          String mergedPayload = mergePayloads(csid, new PoxPayloadOut(payload.getBytes()));

          if (mergedPayload != null) {
            if(updateRecord(csid, mergedPayload) != -1) {
              numAffected += 1;
            } else {
              // Make this more obvious?
              logger.warn("The record with csid " +  csid + " was not updated");
            }
          }
        }
        setCompletionStatus(STATUS_COMPLETE);
        results.setNumAffected(numAffected);
        results.setUserNote("Updated " + numAffected + " records");

        setResults(results);
      } else {
        throw new Exception("Unsupported invocation mode " + mode);
      }
    } catch (Exception e) {
      setCompletionStatus(STATUS_ERROR);
      setErrorInfo(new InvocationError(INT_ERROR_STATUS, e.getMessage()));
    }
  }
  public String preparePayload(HashMap<String, String> fieldsToUpdate)  {
    /* Values that will require special handing:
      pahma: inventoryCount, pahmaFieldLocVerbatim, pahmaEthnographicFileCodeList
      ucbnh naturalhistory: taxon
    
    */ 


    String commonValues = "";
    String ucbnhValues = "";
    String pahmaValues = "";
    Boolean pahma = false;
    Boolean ucbnh = false;

    String otherNumber = "<otherNumberList><otherNumber>";
    Boolean otherNumFlag = false;

    for (String key : fieldsToUpdate.keySet()) {
      String value = fieldsToUpdate.get(key);
      
      if (key.equals("material")) {
        commonValues += "<materialGroupList><materialGroup><" + key + ">" + value + "</" + key + "></materialGroup></materialGroupList>";
      } else if (key.equals("responsibleDepartment")) {
        commonValues += "<responsibleDepartments><" + key + ">" + value + "</" + key + "></responsibleDepartments>";
      } else if (key.equals("assocPeople")) {
        commonValues += "<assocPeopleGroupList><assocPeopleGroup><" + key + ">" + value + "</" + key + "></assocPeopleGroup></assocPeopleGroupList>";
      } else if (key.equals("objectProductionPerson")) {
        commonValues += "<objectProductionPersonGroupList><objectProductionPersonGroup><" + key + ">" + value + "</" + key + "></objectProductionPersonGroup></objectProductionPersonGroupList>";
      } else if (key.equals("objectProductionPlace")) {
        commonValues += "<objectProductionPlaceGroupList><objectProductionPlaceGroup><" + key + ">" + value + "</" + key + "></objectProductionPlaceGroup></objectProductionPlaceGroupList>";
      } else if (key.equals("fieldCollector")) {
        commonValues += "<fieldCollectors><" + key + ">" + value + "</" + key + "></fieldCollectors>";
      } else if (key.equals("objectStatus")) {
        commonValues += "<objectStatusList><" + key + ">" + value + "</" + key + "></objectStatusList>";
      } else if (key.equals("contentPlace")) {
        commonValues += "<contentPlaces><" + key + ">" + value + "</" + key + "></contentPlaces>";
      } else if (key.equals("objectName")) {
        commonValues += "<objectNameList><objectNameGroup><" + key + ">" + value + "</" + key + "></objectNameGroup></objectNameList>";
      } else if (key.equals("briefDescription")) {
        commonValues += "<briefDescriptions><" + key + ">" + value + "</" + key + "></briefDescriptions>";
      } else if (key.equals("numberValue") || key.equals("numberType")) {
        otherNumber += "<" + key + ">" + value + "</" + key + ">";
        otherNumFlag = true;
      } else if (key.equals("objectProductionDate")) {
        commonValues += "<objectProductionDateGroupList><objectProductionDateGroup><dateDisplayDate>" + value + "</dateDisplayDate></objectProductionDateGroup></objectProductionDateGroupList>";
      } else if (key.equals("contentDate")) {
        commonValues += "<contentDateGroup><dateDisplayDate>" + value + "</dateDisplayDate></contentDateGroup>";
      } else if (key.equals("fieldCollectionDateGroup")) {
          commonValues += "<fieldCollectionDateGroup><dateDisplayDate>" + value + "</dateDisplayDate></fieldCollectionDateGroup>";
      } else {
        commonValues += "<" + key + ">" + value + "</" + key + ">";
      }
      // } else if (key.equals("taxonomicIdentGroupList")) {
        // ucbnhValues += "<taxonomicIdentGroup><" + key + ">" + value + "<" + key + "/><taxonomicIdentGroup/>";
        // ucbnh = true;
      // } else if (key.equals("materialGroup")) {
      //   commonValues += "<materialGroupList><" + key + ">" + value + "<" + key + "/><materialGroupList/>";
      // } else if (key.equals("inventoryCount") || key.equals("pahmaFieldLocVerbatim") || key.equals("pahmaEthnographicFileCodeList")) {
      //   pahmaValues += "<" + key + ">" + value + "</" + key + ">";
      //   pahma = true;
      // } else {
      //   commonValues += "<" + key + ">" + value + "</" + key + ">";
      // }
    }

    if (otherNumFlag) {
      otherNumber += "</otherNumber></otherNumberList>";
      commonValues += otherNumber;
    }

    String tenant = "";

    String commonPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
    "<document name=\"collectionobjects\">" +
      "<ns2:collectionobjects_common " +
      "xmlns:ns2=\"http://collectionspace.org/services/collectionobject\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + 
      commonValues +
    "</ns2:collectionobjects_common>";

    if (pahma) {
      commonPayload += "<ns2:collectionobjects_pahma " + 
                        "xmlns:ns2=\"http://collectionspace.org/services/collectionobject/local/pahma\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + pahmaValues + 
                        "</ns2:collectionobjects_pahma>";
    }
    if (ucbnh) {
      commonPayload += "<n2:collectionobjects_naturalhistory " +
                        "xmlns:ns2=\"http://collectionspace.org/services/collectionobject/domain/naturalhistory\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" + ucbnhValues +
                        "</ns2:collectionobjects_naturalhistory>";
    }



    commonPayload += "</document>";
    return commonPayload;
  }

  public String mergePayloads(String csid, PoxPayloadOut batchPayload) throws Exception {
    // now we have the bytes for both t
    HashMap<String, Element> batchElementList = new HashMap<String, Element>();
    PoxPayloadOut collectionObjectPayload = findCollectionObjectByCsid(csid);

    // Now we can go through the parts (_common, _pahma, _ucbnh)
    for (PayloadOutputPart batchCandidatePart : batchPayload.getParts()) {
      Element batchCandidatePartElement = batchCandidatePart.asElement();
      batchElementList.put(batchCandidatePartElement.getName(), batchCandidatePartElement);
    }

    // now, for each (_common, _pahma, _ucbnh, we can iterate through each field)
    for (String batchFieldElem : batchElementList.keySet()) {

      Element objectFieldElement = collectionObjectPayload.getPart(batchFieldElem).asElement();

      Element batchElement = batchElementList.get(batchFieldElem);

      // Now for each 
      for (Element batchElemChild : (List<Element>) batchElement.elements()) {
        String childElemName = batchElemChild.getName(); // Now we need to find the matching element

        if (childElemName == null) {continue;}


        Element collectionObjElementList = objectFieldElement.element(childElemName);

        for (Element objElem : (List<Element>) collectionObjElementList.elements()) {
//          if elements is empty then its not a repeating list
          batchElemChild.add(objElem.createCopy());
        }

        objectFieldElement.remove(collectionObjElementList);
        objectFieldElement.add(batchElemChild.createCopy());

      }

    }

    return collectionObjectPayload.asXML();
  }
  
  public int updateRecord(String csid, String payload) throws URISyntaxException, DocumentException {

    int result = 0;

    try {
      ResourceMap resource = getResourceMap();
      NuxeoBasedResource collectionObjectResource = (NuxeoBasedResource) resource.get(CollectionObjectClient.SERVICE_NAME);

      String response = new String(collectionObjectResource.update(getServiceContext(), resource, createUriInfo(), csid, payload));
    } catch (Exception e) {
      result = -1;
    }
    return result;

  }

  /*
   * @return a HashMap containing (K, V) pairs of  (Field, NewValue)
   */
  public HashMap<String, String>  getValues() {
    HashMap<String, String> results = new HashMap<String,  String>();
    for(Param param : this.getParams()) {
      if (param.getKey() != null) {
        String val = param.getValue();
        if (val != null && !val.equals("")) {
          results.put(param.getKey(), param.getValue());
        }
      }
    }
    return results;
  }
}
package com.forkexec.pts.ws.frontend;

import static javax.xml.ws.BindingProvider.ENDPOINT_ADDRESS_PROPERTY;

import java.util.Map;

import javax.xml.ws.BindingProvider;

import com.forkexec.pts.ws.BadInitFault_Exception;
import com.forkexec.pts.ws.InvalidEmailFault_Exception;
import com.forkexec.pts.ws.InvalidPointsFault_Exception;
import com.forkexec.pts.ws.NotEnoughBalanceFault_Exception;
import com.forkexec.pts.ws.PointsPortType;
import com.forkexec.pts.ws.PointsService;
import com.forkexec.pts.ws.cli.PointsClient;
import com.forkexec.pts.ws.cli.PointsClientException;
import com.forkexec.pts.ws.Value;
import com.forkexec.pts.ws.Tag;

import pt.ulisboa.tecnico.sdis.ws.uddi.UDDINaming;

/**
 * FrontEnd port wrapper.
 *
 * Adds easier end point address configuration to the Port generated by
 * wsimport.
 */
public class PointsFrontEnd {

	/** WS service */
	PointsService service = null;

	/** WS port (port type is the interface, port is the implementation) */
	PointsPortType port = null;

	/** UDDI server URL */
	private String uddiURL = null;

	private int nReplicas = 0;

	private final String POINTS = "A41_Points";

	private final String SUCCESS = "ACK";

	/** output option **/
	private boolean verbose = false;

	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	/** constructor with provided UDDI location and name */
	public PointsFrontEnd(String uddiURL, int N) throws PointsFrontEndException {
		this.uddiURL = uddiURL;
		this.nReplicas = N;
	}

	/** UDDI lookup */
	private String uddiLookup(String wsName) throws PointsFrontEndException {
		String wsURL;
		try {
			if (verbose)
				System.out.printf("Contacting UDDI at %s%n", uddiURL);
			UDDINaming uddiNaming = new UDDINaming(uddiURL);

			if (verbose)
				System.out.printf("Looking for '%s'%n", wsName);
			wsURL = uddiNaming.lookup(wsName);

		} catch (Exception e) {
			String msg = String.format("FrontEnd failed lookup on UDDI at %s!", uddiURL);
			throw new PointsFrontEndException(msg, e);
		}

		if (wsURL == null) {
			String msg = String.format("Service with name %s not found on UDDI at %s", wsName, uddiURL);
			throw new PointsFrontEndException(msg);
		}

		return wsURL;
	}

	// remote invocation methods ----------------------------------------------

	public void activateUser(String userEmail) throws EmailAlreadyExistsFault_Exception, InvalidEmailFault_Exception {
    PointsClient cli = null;
    try {
      //cli = new PointsClient(uddiURL, wsURL);
    } catch (Exception e) {
      // FIXME - IGNORE
    }
    //cli.activateUser(userEmail);
	}

	public int pointsBalance(String userEmail) throws InvalidEmailFault_Exception {
    PointsClient cli = null;
		Value maxValue = null;
		int currentSeq = 0;

    try {
			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				Value value = cli.read(userEmail);
				Tag tag = value.getTag();
				if(  tag.getSeq() > currentSeq ) {
					maxValue = value;
					currentSeq = tag.getSeq();
				}
			}

    } catch (PointsClientException | PointsFrontEndException e) {
			throw new RuntimeException("Failed to lookup Points Service.");
    } catch (InvalidEmailFault_Exception e) { 
			throw new InvalidEmailFault_Exception( e.getMessage(), e.getFaultInfo());
    } 

		return maxValue.getVal();
	}

	public int addPoints(String userEmail, int pointsToAdd)
			throws InvalidEmailFault_Exception, InvalidPointsFault_Exception, NotEnoughBalanceFault_Exception {
		PointsClient cli = null;
		Value maxValue = null;
		int currentSeq = 0;
		int numSuccess = 0;
		int points = 0;

		try {

			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				Value value = cli.read(userEmail);
				Tag tag = value.getTag();
				if(  tag.getSeq() > currentSeq ) {
					maxValue = value;
					currentSeq = tag.getSeq();
				}
			}

			Tag newTag = createTag(maxValue.getTag());
			points = maxValue.getVal() + pointsToAdd;

			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				if ( cli.write(userEmail, points, newTag).equals(SUCCESS) )
					numSuccess ++;
			}

		} catch (PointsClientException | PointsFrontEndException e) {
			throw new RuntimeException("Failed to lookup Points Service.");
		} catch (InvalidPointsFault_Exception e) {
			throw new InvalidPointsFault_Exception( e.getMessage(), e.getFaultInfo());
		} catch (InvalidEmailFault_Exception e) {
			throw new InvalidEmailFault_Exception( e.getMessage(), e.getFaultInfo());
		} catch (NotEnoughBalanceFault_Exception e) {
			throw new NotEnoughBalanceFault_Exception ( e.getMessage(), e.getFaultInfo());
		}

		if( numSuccess == nReplicas)
			return points;

		return -1;
	}

	public int spendPoints(String userEmail, int pointsToSpend)
			throws InvalidEmailFault_Exception, InvalidPointsFault_Exception, NotEnoughBalanceFault_Exception {
    PointsClient cli = null;
		Value maxValue = null;
		int currentSeq = 0;
		int numSuccess = 0;
		int points = 0;

    try {

			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				Value value = cli.read(userEmail);
				Tag tag = value.getTag();
				if(  tag.getSeq() > currentSeq ) {
					maxValue = value;
					currentSeq = tag.getSeq();
				}
			}

			Tag newTag = createTag(maxValue.getTag());
			points = maxValue.getVal() - pointsToSpend;

			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				if ( cli.write(userEmail, points, newTag).equals(SUCCESS) )
					numSuccess ++;
			}

    } catch (PointsClientException | PointsFrontEndException e) {
			throw new RuntimeException("Failed to lookup Points Service.");
    } catch (InvalidPointsFault_Exception e) {
			throw new InvalidPointsFault_Exception( e.getMessage(), e.getFaultInfo());
		} catch (InvalidEmailFault_Exception e) {
			throw new InvalidEmailFault_Exception( e.getMessage(), e.getFaultInfo());
		} catch (NotEnoughBalanceFault_Exception e) {
			throw new NotEnoughBalanceFault_Exception ( e.getMessage(), e.getFaultInfo());
    } 

		if( numSuccess == nReplicas)
			return points;

		return -1;

	}

	public Tag createTag(Tag t) {
		Tag tag = new Tag();
		tag.setSeq(t.getSeq() + 1);
		tag.setCid(0);
		return tag;
	}

	// control operations -----------------------------------------------------

	public String ctrlPing(String inputMessage) {
    PointsClient cli = null;
		StringBuilder builder = new StringBuilder();

    try {
			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				if ( cli.ctrlPing(inputMessage) != null )
					builder.append( cli.ctrlPing(inputMessage) );
			}
    } catch (PointsClientException | PointsFrontEndException e) {
      	throw new RuntimeException("Failed to lookup Points Service.");
    }
		return builder.toString();
	}

	public void ctrlClear() {
    PointsClient cli = null;
    try {
			for(int i = 0; i < nReplicas; i++) {
				cli = new PointsClient( uddiLookup(POINTS + Integer.toString(i+1) ) );
				cli.ctrlClear();
			}
    } catch (PointsClientException | PointsFrontEndException e) {
      	throw new RuntimeException("Failed to lookup Points Service.");
    }
	}

	public void ctrlInit(int startPoints) throws BadInitFault_Exception {
    PointsClient cli = null;
    try {
      //cli = new PointsClient(uddiURL, wsURL);
    } catch (Exception e) {
      // FIXME - IGNORE
    }
    cli.ctrlInit(startPoints);
	}

}

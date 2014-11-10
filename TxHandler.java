import java.util.ArrayList; 

public class TxHandler {

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		UTXOPool pool = UTXOPool(utxoPool);
	}

	/* Returns true if 
	 * (1) all outputs claimed by tx are in the current UTXO pool, 
	 * (2) the signatures on each input of tx are valid, 
	 * (3) no UTXO is claimed multiple times by tx, 
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of   
	        its output values;
	   and false otherwise.
	 */

	public boolean isValidTx(Transaction tx) {
		double outSum = 0;
		double inSum = 0;
		int index = 0;
		byte[] hash = tx.getHash();


		UTXOPool currPool = new UTXOPool();
		
		ArrayList<Output> outputArray = tx.getOutputs();
		for (Output out : outputArray) {
			UTXO current = new UTXO(hash, index);

			// Checks condition (1): all outputs claimed by tx are in the current UTXO pool
			if (!pool.contains(out))
				return false;			

			// Checks condition (3): no UTXO is claimed multiple times by tx, 
			if (currPool.contains(current)) 
				return false;

			// Checks condition (4): all the txns outputs are non-negative
			if (out.value < 0) {
				return false;
			}
			outSum += out.value;

			currPool.addUTXO(hash, outputArray);
			index++;
		}

		// sum the inputs
		ArrayList<Input> inputArray = tx.getInputs();
		for (Input in : inputArray) {
			ArrayList<Output> prevOut = 
			// get prev transaction in the pool
			// get the index of the output
			
			// add that to the inSum
			inSum += in.value;

			// Checks condition (2): the signatures on each input of tx are valid
			//get the message and the signature and then verify it
			byte[] sig = tx.getRawDataToSign(in.outputIndex);
			if (sig != in.signature) {
				return false;
			}

		}

		// Checks condition (5): the sum of tx’s input values is 
		// greater than or equal to the sum of its output values
		if (outSum < inSum) 
			return false;
		return true;
	}

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		ArrayList<Transaction> acceptedTx = new ArrayList<Transaction>();
		for (Transaction tx : possibleTxs) {
			if (!pool.isValidTx(tx)) {
				continue;
			}
			//remove all inputs
			//add all outputs
			acceptedTx.add(tx);
		}

		return acceptedTx.toArray();
	}

} 

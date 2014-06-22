package net.aclib.fanova;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.collections.map.HashedMap;

import ca.ubc.cs.beta.aclib.configspace.NormalizedRange;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import net.aclib.fanova.model.FunctionalANOVAVarianceDecompose;

public class FanovaRemote {
	
	private FunctionalANOVAVarianceDecompose favd;
	private IPCMechanism ipc;
	private ParamConfigurationSpace configSpace;
	private HashMap<String, Integer> param2dim = new HashMap<String, Integer>();
	
	public FanovaRemote(FunctionalANOVAVarianceDecompose favd, IPCMechanism ipc, ParamConfigurationSpace configSpace) {
		super();
		this.favd = favd;
		this.ipc = ipc;
		this.configSpace = configSpace;
		
		for (int i = 0; i < this.configSpace.getParameterNames().size(); i++) {
			param2dim.put(this.configSpace.getParameterNames().get(i), i);
		}
	}
	
	public void run() {
		while(true) {
			String msg = this.ipc.receive();
			if (msg == null) {
				continue;
			}
			String[] commandAndArgs = new String[0];
			String command;
			if  (msg.contains(":")) {
				commandAndArgs = msg.split(":");
				command = commandAndArgs[0];
			} else {
				command = msg;
			}

			if(command.equals("get_marginal")) {
				assert commandAndArgs.length == 2;
				int dim = Integer.valueOf(commandAndArgs[1]);
				double marginal = this.favd.getMarginal(dim);
				this.ipc.send(String.valueOf(marginal)+"\n");
			} else if(command.equals("get_pairwise_marginal")) {
				assert commandAndArgs.length == 3;
				int dim1 = Integer.valueOf(commandAndArgs[1]);
				int dim2 = Integer.valueOf(commandAndArgs[2]);
				double marginal = this.favd.getPairwiseMarginal(dim1, dim2);
				this.ipc.send(String.valueOf(marginal)+"\n");
			} else if(command.equals("die")) {
				System.out.println("QUITTING");
				return;
			} else if(command.equals("unormalize_value")){
				assert commandAndArgs.length == 3;
				String parameterName = commandAndArgs[1];
				double normalizedValue = Double.valueOf(commandAndArgs[2]);
				if (this.configSpace.getNormalizedRangeMap().containsKey(parameterName))
				{
					NormalizedRange range = this.configSpace.getNormalizedRangeMap().get(parameterName);
					this.ipc.send(range.unnormalizeValue(normalizedValue) + "\n" );
				}
				else {
					this.ipc.send("\n");
				}
				
			} else if(command.equals("get_parameter_names")){
				StringBuilder sb = new StringBuilder();
				for (String name : this.configSpace.getParameterNames()) {
					name = name.replace(';', '_');
					sb.append(name);
					sb.append(';');
				}
				if (sb.length() != 0){
					sb.deleteCharAt(sb.length() - 1);
				}
				this.ipc.send(sb.toString() + "\n");
			} else if(command.equals("get_categorical_parameters")){
				StringBuilder sb = new StringBuilder();
				int[] categoricalSize = this.configSpace.getCategoricalSize();
				for (int i = 0; i < categoricalSize.length; i++) {
					if (categoricalSize[i] > 0)
					{
						sb.append(this.configSpace.getParameterNames().get(i).replace(';', '_'));
						sb.append(';');
					}
				}
				if (sb.length() != 0){
					sb.deleteCharAt(sb.length() - 1);
				}
				this.ipc.send(sb.toString() + "\n");
			} else if(command.equals("get_categorical_size")){
				assert commandAndArgs.length == 2;
				String parameterName = commandAndArgs[1];
				int[] categoricalSize = this.configSpace.getCategoricalSize();
				if (param2dim.containsKey(parameterName)) {
					int dim = param2dim.get(parameterName);
					this.ipc.send(categoricalSize[dim] + "\n");
				} else {
					this.ipc.send("\n");
				}
			} else if(command.equals("get_categorical_values")){
				assert commandAndArgs.length == 2;
				String parameterName = commandAndArgs[1];
				StringBuilder sb = new StringBuilder();
				Map<String, Map<String, Integer>> valueMap = this.configSpace.getCategoricalValueMap();
				if (valueMap.containsKey(parameterName)) {
					Map<String, Integer> parameterValueMap = valueMap.get(parameterName);
					//sort by index:
					Vector<Entry<String, Integer>> entries = new Vector<Entry<String, Integer>>(parameterValueMap.entrySet());
					Collections.sort(entries, new Comparator<Entry<String, Integer>>() {
						@Override
						public int compare(Entry<String, Integer> o1,
								Entry<String, Integer> o2) {
							return o1.getValue() - o2.getValue();
						}
					});
					for (Entry<String, Integer> entry : entries) {
						System.out.println(entry.getKey() + " " + entry.getValue());
						sb.append(entry.getKey());
						sb.append(';');
					}
					if (sb.length() != 0){
						sb.deleteCharAt(sb.length() - 1);
					}
				}
				
				this.ipc.send(sb.toString() + "\n");
			} else if(command.equals("get_continuous_parameters")){
				StringBuilder sb = new StringBuilder();
				int[] categoricalSize = this.configSpace.getCategoricalSize();
				Map<String, NormalizedRange> normMap = this.configSpace.getNormalizedRangeMap();
				for (int i = 0; i < categoricalSize.length; i++) {
					String paramName = this.configSpace.getParameterNames().get(i);
					if (categoricalSize[i] == 0 && !normMap.get(paramName).isIntegerOnly())
					{
						sb.append(paramName.replace(';', '_'));
						sb.append(';');
					}
				}
				if (sb.length() != 0){
					sb.deleteCharAt(sb.length() - 1);
				}
				this.ipc.send(sb.toString() + "\n");
			} else if(command.equals("get_integer_parameters")){
				StringBuilder sb = new StringBuilder();
				int[] categoricalSize = this.configSpace.getCategoricalSize();
				Map<String, NormalizedRange> normMap = this.configSpace.getNormalizedRangeMap();
				for (int i = 0; i < categoricalSize.length; i++) {
					String paramName = this.configSpace.getParameterNames().get(i);
					if (categoricalSize[i] == 0 && normMap.get(paramName).isIntegerOnly())
					{
						sb.append(paramName.replace(';', '_'));
						sb.append(';');
					}
				}
				if (sb.length() != 0){
					sb.deleteCharAt(sb.length() - 1);
				}
				this.ipc.send(sb.toString() + "\n");
			} else if(command.equals("get_marginal_for_value")){
				assert commandAndArgs.length == 3;
				int dim = Integer.valueOf(commandAndArgs[1]);
				double valueToPredict = Double.valueOf(commandAndArgs[2]);
				double [] meanAndStd = this.favd.getMarginalForValue(dim, valueToPredict);
				String send_msg = meanAndStd[0] + ";" + meanAndStd[1] + "\n";
				this.ipc.send(send_msg);
			} else if(command.equals("get_marginal_for_value_pair")){
				assert commandAndArgs.length == 5;
				int dim1 = Integer.valueOf(commandAndArgs[1]);
				int dim2 = Integer.valueOf(commandAndArgs[2]);
				double valueToPredict1 = Double.valueOf(commandAndArgs[3]);
				double valueToPredict2 = Double.valueOf(commandAndArgs[4]);
				double [] meanAndStd = this.favd.getMarginalForValuePair(dim1, dim2, valueToPredict1, valueToPredict2);
				String send_msg = meanAndStd[0] + ";" + meanAndStd[1] + "\n";
				this.ipc.send(send_msg);
			}
		}
	}

}

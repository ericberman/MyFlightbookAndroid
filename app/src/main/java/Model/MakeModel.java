package Model;

import java.io.Serializable;
import java.util.Hashtable;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

public class MakeModel extends SoapableObject implements KvmSerializable, Serializable, Comparable<MakeModel> {

	private static final long serialVersionUID = 1L;
	public String Description = "";
	public int MakeModelId = -1;
	
	public final String KEY_MODELID = "MakeModelID";
	public final String KEY_DESCRIPTION = "ModelName";
	
	public enum MakeModelProp {pidMakeModelID, pidModelName}

	public MakeModel()
	{
		super();
	}
	
	public MakeModel(SoapObject so)
	{
		super();
		FromProperties(so);
	}
	
	public MakeModel(MakesandModels mm)
	{
		super();
		MakeModelId = mm.ModelId;
		Description = mm.Description;
	}

	@Override
	public String toString()
	{
		return Description;
	}
	
	@Override
	public void ToProperties(SoapObject so) {
		so.addProperty(KEY_DESCRIPTION, Description);
		so.addProperty(KEY_MODELID, MakeModelId);
	}

	@Override
	public void FromProperties(SoapObject so) {
		Description = so.getProperty(KEY_DESCRIPTION).toString();
		MakeModelId = Integer.parseInt(so.getProperty(KEY_MODELID).toString());
	}
	
	// serialization methods
	public int getPropertyCount()
	{
		return MakeModelProp.values().length;
	}
	
	public Object getProperty(int i)
	{
		MakeModelProp mmp = MakeModelProp.values()[i];
		switch (mmp) {
		case pidMakeModelID:
			return MakeModelId;
		case pidModelName:
			return Description;
		default:
			return null;
		}
	}
	
	public void setProperty(int i, Object value)
	{
		MakeModelProp mmp = MakeModelProp.values()[i];
		String sz = value.toString();
		switch (mmp) {
		case pidMakeModelID:
			MakeModelId = Integer.parseInt(sz);
			break;
		case pidModelName:
			Description = sz;
			break;
		default:
			break;
		}
	}
	
	public void getPropertyInfo(int i, @SuppressWarnings("rawtypes") Hashtable h, PropertyInfo pi)
	{
		MakeModelProp mmp = MakeModelProp.values()[i];
		switch (mmp) {
		case pidMakeModelID:
			pi.type = PropertyInfo.INTEGER_CLASS;
			pi.name = "MakeModelID";
			break;
		case pidModelName:
			pi.type = PropertyInfo.STRING_CLASS;
			pi.name = "ModelName";
			break;
		default:
			break;
		}
	}

	public int compareTo(MakeModel another) {
		return this.Description.compareToIgnoreCase(another.Description);
	}
}

package com.kettle.core.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.pentaho.di.core.Condition;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.NotePadMeta;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.ValueMetaAndData;
import org.pentaho.di.core.util.EnvUtil;
import org.pentaho.di.repository.RepositoryMeta;
import org.pentaho.di.repository.kdr.KettleDatabaseRepository;
import org.pentaho.di.repository.kdr.KettleDatabaseRepositoryMeta;
import org.pentaho.di.trans.TransHopMeta;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.delete.DeleteMeta;
import org.pentaho.di.trans.steps.dummytrans.DummyTransMeta;
import org.pentaho.di.trans.steps.filterrows.FilterRowsMeta;
import org.pentaho.di.trans.steps.mergerows.MergeRowsMeta;
import org.pentaho.di.trans.steps.selectvalues.SelectMetadataChange;
import org.pentaho.di.trans.steps.selectvalues.SelectValuesMeta;
import org.pentaho.di.trans.steps.tableinput.TableInputMeta;
import org.pentaho.di.trans.steps.tableoutput.TableOutputMeta;
import org.pentaho.di.trans.steps.update.UpdateMeta;

import com.kettle.bean.KettleTransRecord;
import com.kettle.bean.KettleTransResult;
import com.kettle.core.repo.KettleDBRepositoryClient;
import com.kettle.remote.KettleRemotePool;

/**
 * Kettle数据迁移管理者
 * 
 * @author Administrator
 *
 */
public class KettleMgrInstance {

	/**
	 * 实例
	 */
	private static KettleMgrInstance instance = null;

	/**
	 * 资源库
	 */
	private KettleDatabaseRepository repository = null;

	/**
	 * 远程执行池
	 */
	private KettleRemotePool kettleRemotePool;

	/**
	 * 资源池数据库连接
	 */
	private KettleDBRepositoryClient dbRepositoryClient;

	static {
		getInstance();
	}

	public static KettleMgrInstance getInstance() {
		if (instance == null) {
			instance = new KettleMgrInstance();
		}
		return instance;
	}

	private KettleMgrInstance() {
		init();
	}

	private void init() {
		try {
			KettleEnvironment.init();
			repository = new KettleDatabaseRepository();
			RepositoryMeta dbrepositoryMeta = new KettleDatabaseRepositoryMeta(
					EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_META_ID"),
					EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_META_NAME"),
					EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_META_DESCRIPTION"),
					new DatabaseMeta(EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_NAME"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_TYPE"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_ACCESS"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_HOST"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_DATABASENAME"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_PORT"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_USER"),
							EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_DB_PASSWD")));
			repository.init(dbrepositoryMeta);
			repository.connect(EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_USER"),
					EnvUtil.getSystemProperty("KETTLE_DATABASE_REPOSITORY_PASSWD"));
			dbRepositoryClient = new KettleDBRepositoryClient(repository);
			kettleRemotePool = new KettleRemotePool(dbRepositoryClient, null, null);
			// kettleClusterPool = new KettleClusterPool(repository, null,
			// null);
		} catch (KettleException ex) {
			throw new RuntimeException("KettleMgrInstance初始化失败", ex);
		}
	}

	/**
	 * 远程发送并执行
	 * 
	 * @param transMeta
	 * @return
	 * @throws Exception
	 * @throws KettleException
	 */
	public KettleTransResult remoteSendTrans(TransMeta transMeta) throws KettleException, Exception {
		KettleTransRecord kettleTransBean;
		kettleTransBean = kettleRemotePool.remoteSendTrans(transMeta);
		KettleTransResult kettleTransResult = new KettleTransResult();
		kettleTransResult.setTransID(kettleTransBean.getTransId());
		kettleTransResult.setStatus(kettleTransBean.getStatus());
		return kettleTransResult;
	}

	/**
	 * @param source
	 * @param target
	 * @return
	 * @throws KettleException
	 */
	public KettleTransResult createDataTransfer(KettleDBTranDescribe source, KettleDBTranDescribe target)
			throws KettleException {
		String uuid = UUID.randomUUID().toString().replace("-", "");
		TransMeta transMeta = null;
		try {
			dbRepositoryClient.connect();
			transMeta = new TransMeta();
			transMeta.setName("YHHX-" + uuid);
			DatabaseMeta sourceDataBase = new DatabaseMeta(source.getHost() + "_" + source.getDatabase(),
					source.getType(), "Native", source.getHost(), source.getDatabase(), source.getPort(),
					source.getUser(), source.getPasswd());
			transMeta.addDatabase(sourceDataBase);
			DatabaseMeta targetDatabase = new DatabaseMeta(target.getHost() + "_" + target.getDatabase(),
					target.getType(), "Native", target.getHost(), target.getDatabase(), target.getPort(),
					target.getUser(), target.getPasswd());
			transMeta.addDatabase(targetDatabase);
			/*
			 * 获取非PK列
			 */
			String[] allColumns = target.getColumns().toArray(new String[0]);
			/*
			 * 获取非PK列
			 */
			List<String> valueFields = new ArrayList<String>();
			valueFields.addAll(target.getColumns());
			valueFields.removeAll(target.getPkcolumns());
			String[] valueColumns = valueFields.toArray(new String[0]);
			/*
			 * 获取PK列
			 */
			String[] pkColumns = target.getPkcolumns().toArray(new String[0]);
			/*
			 * 条件
			 */
			String[] conditions = new String[target.getPkcolumns().size()];
			for (int i = 0; i < conditions.length; i++) {
				conditions[i] = "=";
			}
			/*
			 * Note
			 */
			String startNote = "Start " + transMeta.getName();
			NotePadMeta ni = new NotePadMeta(startNote, 150, 10, -1, -1);
			transMeta.addNote(ni);
			/*
			 * source
			 */
			TableInputMeta tii = new TableInputMeta();
			tii.setDatabaseMeta(sourceDataBase);
			String selectSQL = source.getSql();
			tii.setSQL(selectSQL);
			StepMeta query = new StepMeta("source", tii);
			query.setLocation(150, 100);
			query.setDraw(true);
			query.setDescription("STEP-SOURCE");
			transMeta.addStep(query);

			/*
			 * 转换名称
			 */
			String[] sourceFields = source.getColumns().toArray(new String[0]);
			String[] targetFields = target.getColumns() == null ? sourceFields
					: target.getColumns().toArray(new String[0]);
			int[] targetPrecisions = new int[sourceFields.length];
			int[] targetLengths = new int[targetFields.length];
			SelectValuesMeta svi = new SelectValuesMeta();
			svi.setSelectLength(targetLengths);
			svi.setSelectPrecision(targetPrecisions);
			svi.setSelectName(sourceFields);
			svi.setSelectRename(targetFields);
			svi.setDeleteName(new String[0]);
			svi.setMeta(new SelectMetadataChange[0]);
			StepMeta chose = new StepMeta("chose", svi);
			chose.setLocation(350, 100);
			chose.setDraw(true);
			chose.setDescription("STEP-CHOSE");
			transMeta.addStep(chose);
			transMeta.addTransHop(new TransHopMeta(query, chose));

			/*
			 * target
			 */
			TableInputMeta targettii = new TableInputMeta();
			targettii.setDatabaseMeta(targetDatabase);
			targettii.setSQL(target.getSql());
			StepMeta targetQuery = new StepMeta("target", targettii);
			transMeta.addStep(targetQuery);
			targetQuery.setLocation(350, 300);
			targetQuery.setDraw(true);
			targetQuery.setDescription("STEP-TARGET");

			/*
			 * merage
			 */
			MergeRowsMeta mrm = new MergeRowsMeta();
			mrm.setFlagField("flagfield");
			mrm.setValueFields(valueColumns);
			mrm.setKeyFields(pkColumns);
			mrm.getStepIOMeta().setInfoSteps(new StepMeta[] { targetQuery, chose });
			StepMeta merage = new StepMeta("merage", mrm);
			transMeta.addStep(merage);
			merage.setLocation(650, 100);
			merage.setDraw(true);
			merage.setDescription("STEP-MERAGE");
			transMeta.addTransHop(new TransHopMeta(chose, merage));
			transMeta.addTransHop(new TransHopMeta(targetQuery, merage));

			/*
			 * noChange
			 */
			FilterRowsMeta frm_nochange = new FilterRowsMeta();
			frm_nochange.setCondition(new Condition("flagfield", Condition.FUNC_EQUAL, null,
					new ValueMetaAndData("constant", "identical")));
			StepMeta nochang = new StepMeta("nochang", frm_nochange);
			nochang.setLocation(950, 100);
			nochang.setDraw(true);
			nochang.setDescription("STEP-NOCHANGE");
			transMeta.addStep(nochang);
			transMeta.addTransHop(new TransHopMeta(merage, nochang));
			/*
			 * nothing
			 */
			StepMeta nothing = new StepMeta("nothing", new DummyTransMeta());
			nothing.setLocation(950, 300);
			nothing.setDraw(true);
			nothing.setDescription("STEP-NOTHING");
			transMeta.addStep(nothing);
			transMeta.addTransHop(new TransHopMeta(nochang, nothing));
			frm_nochange.getStepIOMeta().getTargetStreams().get(0).setStepMeta(nothing);
			/*
			 * isNew
			 */
			FilterRowsMeta frm_new = new FilterRowsMeta();
			frm_new.setCondition(
					new Condition("flagfield", Condition.FUNC_EQUAL, null, new ValueMetaAndData("constant", "new")));
			StepMeta isNew = new StepMeta("isNew", frm_new);
			isNew.setLocation(1250, 100);
			isNew.setDraw(true);
			isNew.setDescription("STEP-ISNEW");
			transMeta.addStep(isNew);
			transMeta.addTransHop(new TransHopMeta(nochang, isNew));
			frm_nochange.getStepIOMeta().getTargetStreams().get(1).setStepMeta(isNew);
			/*
			 * insert
			 */
			TableOutputMeta toi = new TableOutputMeta();
			toi.setDatabaseMeta(targetDatabase);
			toi.setTableName(target.getTableName());
			toi.setCommitSize(100);
			toi.setTruncateTable(false);
			toi.setSpecifyFields(true);
			toi.setFieldDatabase(targetFields);
			toi.setFieldStream(targetFields);
			StepMeta insert = new StepMeta("insert", toi);
			insert.setLocation(1250, 300);
			insert.setDraw(true);
			insert.setDescription("STEP-INSERT");
			transMeta.addStep(insert);
			transMeta.addTransHop(new TransHopMeta(isNew, insert));
			frm_new.getStepIOMeta().getTargetStreams().get(0).setStepMeta(insert);
			/*
			 * isChange
			 */
			FilterRowsMeta frm_isChange = new FilterRowsMeta();
			frm_isChange.setCondition(new Condition("flagfield", Condition.FUNC_EQUAL, null,
					new ValueMetaAndData("constant", "changed")));
			StepMeta isChange = new StepMeta("isChange", frm_isChange);
			isChange.setLocation(1550, 100);
			isChange.setDraw(true);
			isChange.setDescription("STEP-ISCHANGE");
			transMeta.addStep(isChange);
			transMeta.addTransHop(new TransHopMeta(isNew, isChange));
			frm_new.getStepIOMeta().getTargetStreams().get(1).setStepMeta(isChange);
			/*
			 * update
			 */
			UpdateMeta um = new UpdateMeta();
			um.setDatabaseMeta(targetDatabase);
			um.setUseBatchUpdate(true);
			um.setTableName(target.getTableName());
			um.setCommitSize("100");
			um.setKeyLookup(pkColumns);
			um.setKeyStream(pkColumns);
			um.setKeyCondition(conditions);
			um.setKeyStream2(new String[pkColumns.length]);
			um.setUseBatchUpdate(true);
			um.setUpdateLookup(allColumns);
			um.setUpdateStream(allColumns);

			StepMeta update = new StepMeta("update", um);
			update.setLocation(1850, 300);
			update.setDraw(true);
			update.setDescription("STEP-UPDATE");
			transMeta.addStep(update);
			transMeta.addTransHop(new TransHopMeta(isChange, update));
			frm_isChange.getStepIOMeta().getTargetStreams().get(0).setStepMeta(update);
			/*
			 * delete
			 */
			DeleteMeta dm = new DeleteMeta();
			dm.setDatabaseMeta(targetDatabase);
			dm.setTableName(target.getTableName());
			dm.setCommitSize("100");
			dm.setKeyCondition(conditions);
			dm.setKeyLookup(pkColumns);
			dm.setKeyStream2(new String[target.getPkcolumns().size()]);
			dm.setKeyStream(pkColumns);
			StepMeta delete = new StepMeta("delete", dm);
			delete.setLocation(1550, 300);
			delete.setDraw(true);
			delete.setDescription("STEP-DELETE");
			transMeta.addStep(delete);
			transMeta.addTransHop(new TransHopMeta(isChange, delete));
			frm_isChange.getStepIOMeta().getTargetStreams().get(1).setStepMeta(delete);
			KettleTransResult result = remoteSendTrans(transMeta);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			throw new KettleException("==========!==========");
		}
	}

	/**
	 * 查询数据迁移
	 * 
	 * @param transID
	 * @return
	 * @throws KettleException
	 */
	public KettleTransResult queryDataTransfer(long transID) throws KettleException {
		dbRepositoryClient.connect();
		KettleTransRecord bean = null;
		repository.connect("admin", "admin");
		bean = dbRepositoryClient.queryTransRecord(transID);
		if (bean == null) {
			return null;
		}
		KettleTransResult result = new KettleTransResult();
		result.setTransID(transID);
		result.setStatus(bean.getStatus());
		return result;
	}

	/**
	 * 删除数据迁移
	 * 
	 * @param transID
	 * @return
	 * @throws KettleException
	 */
	public void deleteDataTransfer(long transID) throws KettleException {
		repository.connect("admin", "admin");
		dbRepositoryClient.deleteTransRecord(transID);
	}
}
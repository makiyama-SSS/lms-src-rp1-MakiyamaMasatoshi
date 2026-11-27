package jp.co.sss.lms.form;

import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;

/**
 * 勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class AttendanceForm {

	/** LMSユーザーID */
	private Integer lmsUserId;
	/** グループID */
	private Integer groupId;
	/** 年間計画No */
	private String nenkanKeikakuNo;
	/** ユーザー名 */
	private String userName;
	/** 退校フラグ */
	private Integer leaveFlg;
	/** 退校日 */
	private String leaveDate;
	/** 退校日（表示用） */
	// 牧山聖冬 - Task.26 プルダウン用出退勤時間
	private String dispLeaveDate;
	/** 出勤時間（時プルダウン用） */
	private LinkedHashMap<Integer, String> trainingStartTimeHours;
	/** 出勤時間（分プルダウン用） */
	private LinkedHashMap<Integer, String> trainingStartTimeMinutes;
	/** 退勤時間（時プルダウン用） */
	private LinkedHashMap<Integer, String> trainingEndTimeHours;
	/** 退勤時間（分プルダウン用） */
	private LinkedHashMap<Integer, String> trainingEndTimeMinutes;
	/** 中抜け時間(プルダウン) */
	private LinkedHashMap<Integer, String> blankTimes;
	/** 日次の勤怠フォームリスト */
	private List<DailyAttendanceForm> attendanceList;

}

package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		// 牧山聖冬 - Task.26
		attendanceForm.setTrainingStartTimeHours(attendanceUtil.getHourMap());
		attendanceForm.setTrainingStartTimeMinutes(attendanceUtil.getMinuteMap());
		attendanceForm.setTrainingEndTimeHours(attendanceUtil.getHourMap());
		attendanceForm.setTrainingEndTimeMinutes(attendanceUtil.getMinuteMap());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			if (attendanceManagementDto.getTrainingStartTime() != null) {
				dailyAttendanceForm.setTrainingStartTimeHour(
						attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime())
						);
				dailyAttendanceForm.setTrainingStartTimeMinute(
						attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime())
						);
			}
			if (attendanceManagementDto.getTrainingEndTime() != null) {
				dailyAttendanceForm.setTrainingEndTimeHour(
						attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime())
						);
				dailyAttendanceForm.setTrainingEndTimeMinute(
						attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime())
						);
			}
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 牧山聖冬 - Task.26 退勤時刻
			TrainingTime trainingStartTime = new TrainingTime(null);
			if (dailyAttendanceForm.getTrainingStartTimeHour() != null &&
					dailyAttendanceForm.getTrainingStartTimeMinute() != null) {
				trainingStartTime = new TrainingTime(
						String.valueOf(dailyAttendanceForm.getTrainingStartTimeHour()) +
						":"+
						String.valueOf(dailyAttendanceForm.getTrainingStartTimeMinute()));
			}
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 牧山聖冬 - Task.26 退勤時刻
			TrainingTime trainingEndTime = new TrainingTime(null);
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			if (dailyAttendanceForm.getTrainingEndTimeHour() != null &&
					dailyAttendanceForm.getTrainingEndTimeMinute() != null) {
				trainingEndTime = new TrainingTime(
						String.valueOf(dailyAttendanceForm.getTrainingEndTimeHour()) + 
						":" +
						String.valueOf(dailyAttendanceForm.getTrainingEndTimeMinute()));
			}
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * Task.25 過去日の勤怠に未入力があるかチェック
	 * 
	 * @author 牧山聖冬 - Task.25
	 * @return Boolean 過去の勤怠に未入力があるか
	 * @throws ParseException
	 */
	public Boolean notEnterCheck() throws ParseException {
		// 返却値の変更
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String today = sdf.format(now);
		now = sdf.parse(today);
		
		Integer count = tStudentAttendanceMapper.notEnterCount(
					loginUserDto.getLmsUserId(),
					Constants.DB_FLG_FALSE,
					now);
		return count > 0;
		
	}
	
	/**
	 * Task.27 勤怠入力チェック
	 */
	public void updateInputCheck(
			AttendanceForm attendanceForm,
			BindingResult result
			) {
		/** ロガー */
		final Logger log = LoggerFactory.getLogger(StudentAttendanceService.class);
//		[loop] DailyAttendanceForm ごとにチェックを実施。 
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
//	    * 備考の文字数チェック（100文字以内）。 
			log.info("updateInputCheck() dailyAttendanceForm.getNote().length() = " + dailyAttendanceForm.getNote().length());
			log.info("updateInputCheck() (dailyAttendanceForm.getNote().length() > 0" + (dailyAttendanceForm.getNote().length() > 10));
			if (dailyAttendanceForm.getNote().length() > 100) {
				String note = "備考";
				String length = "100";
				dailyAttendanceForm.setNoteError(true);
				result.addError(new FieldError(result.getObjectName(), "note", messageUtil.getMessage("maxlength", new String[] {note ,length})));
			}
//	    * 時刻の「時」だけ、「分」だけといった片側未入力チェック。（出勤時間）
			if (
				dailyAttendanceForm.getTrainingStartTimeHour() != null && dailyAttendanceForm.getTrainingStartTimeMinute() == null 
				) {
				dailyAttendanceForm.setTrainingStartTimeMinuteError(true);
				result.addError(new FieldError(result.getObjectName(), "startTimeError",messageUtil.getMessage("input.invalid", new String[] {"出勤時間"})));
			}
			if (
					dailyAttendanceForm.getTrainingStartTimeHour() == null && dailyAttendanceForm.getTrainingStartTimeMinute() != null
				) {
					dailyAttendanceForm.setTrainingStartTimeHourError(true);
					result.addError(new FieldError(result.getObjectName(), "startTimeError",messageUtil.getMessage("input.invalid", new String[] {"出勤時間"})));
				}
//		* 時刻の「時」だけ、「分」だけといった片側未入力チェック。（退勤時間）
			if (
					dailyAttendanceForm.getTrainingEndTimeHour() != null && dailyAttendanceForm.getTrainingEndTimeMinute() == null 
				) {
					dailyAttendanceForm.setTrainingEndTimeMinuteError(true);
					result.addError(new FieldError(result.getObjectName(), "endTimeError",messageUtil.getMessage("input.invalid", new String[] {"退勤時間"})));
				}
			if (
					dailyAttendanceForm.getTrainingEndTimeHour() == null && dailyAttendanceForm.getTrainingEndTimeMinute() != null
				) {
					dailyAttendanceForm.setTrainingEndTimeHourError(true);
					result.addError(new FieldError(result.getObjectName(), "endTimeError",messageUtil.getMessage("input.invalid", new String[] {"退勤時間"})));
				}
			
			// 矛盾チェック用変数
			boolean hasStartTime = dailyAttendanceForm.getTrainingStartTimeHour() != null && dailyAttendanceForm.getTrainingStartTimeMinute() != null;
			boolean hasEndTime = dailyAttendanceForm.getTrainingEndTimeHour() != null && dailyAttendanceForm.getTrainingEndTimeMinute() != null;
			boolean hasBlankTime = dailyAttendanceForm.getBlankTime() != null;
//	    * 「出勤なし、退勤あり」の矛盾チェック。  
			if (!hasStartTime && hasEndTime) {
				dailyAttendanceForm.setTrainingStartTimeHourError(true);
				dailyAttendanceForm.setTrainingStartTimeMinuteError(true);
				result.addError(new FieldError(result.getObjectName(), "notStartTimeError",messageUtil.getMessage("attendance.punchInEmpty")));
			}
//	    * 「出勤・退勤ともに未入力、中抜け時間あり」の矛盾チェック。
			if (!hasStartTime && !hasEndTime && hasBlankTime) {
				
				dailyAttendanceForm.setTrainingStartTimeHourError(true);
				dailyAttendanceForm.setTrainingStartTimeMinuteError(true);
				dailyAttendanceForm.setTrainingEndTimeHourError(true);
				dailyAttendanceForm.setTrainingEndTimeMinuteError(true);

				result.addError(new FieldError(result.getObjectName(), "onlyBlankTimeError",messageUtil.getMessage("input.invalid", new String[] {"出勤時間"})));
				result.addError(new FieldError(result.getObjectName(), "onlyBlankTimeError",messageUtil.getMessage("input.invalid", new String[] {"退勤時間"})));
			}
//	    * [if エラーがなければ] 出勤時間・退勤時間の「両方に入力がある場合のみ」、出勤時刻 ＞ 退勤時刻 になっていないか比較チェック。
			if(!result.hasErrors() && hasStartTime && hasEndTime) {
				int startMinutes = dailyAttendanceForm.getTrainingStartTimeHour() * 60 + dailyAttendanceForm.getTrainingStartTimeMinute();
				int endMinutes = dailyAttendanceForm.getTrainingEndTimeHour() * 60 + dailyAttendanceForm.getTrainingEndTimeMinute();
				
				String formattedStartTime = String.format(
						"%02d:%02d",
						dailyAttendanceForm.getTrainingStartTimeHour(),
						dailyAttendanceForm.getTrainingStartTimeMinute());
				
				String formattedEndTime = String.format(
						"%02d:%02d",
						dailyAttendanceForm.getTrainingEndTimeHour(),
						dailyAttendanceForm.getTrainingEndTimeMinute());
				
				if (endMinutes < startMinutes) {
					dailyAttendanceForm.setTrainingStartTimeHourError(true);
					dailyAttendanceForm.setTrainingStartTimeMinuteError(true);
					dailyAttendanceForm.setTrainingEndTimeHourError(true);
					dailyAttendanceForm.setTrainingEndTimeMinuteError(true);
					result.addError(new FieldError(result.getObjectName(), "onlyBlankTimeError",messageUtil.getMessage("attendance.trainingTimeRange", new String[] {formattedStartTime,formattedEndTime})));
				}
			}
//	    * [if 中抜け時間が入力されている場合] 受講時間よりも中抜け時間が長くないか
			if(hasStartTime && hasEndTime && hasBlankTime) {
				int startMinutes = dailyAttendanceForm.getTrainingStartTimeHour() * 60 + dailyAttendanceForm.getTrainingStartTimeMinute();
				int endMinutes = dailyAttendanceForm.getTrainingEndTimeHour() * 60 + dailyAttendanceForm.getTrainingEndTimeMinute();
				int workSpanMinutes = endMinutes - startMinutes;
				
				if ( dailyAttendanceForm.getBlankTime() > workSpanMinutes) {

					dailyAttendanceForm.setTrainingStartTimeHourError(true);
					dailyAttendanceForm.setTrainingStartTimeMinuteError(true);
					dailyAttendanceForm.setTrainingEndTimeHourError(true);
					dailyAttendanceForm.setTrainingEndTimeMinuteError(true);
					dailyAttendanceForm.setBlankTimeError(true);
					result.addError(new FieldError(result.getObjectName(), "overBlankTimeError",messageUtil.getMessage("attendance.blankTimeError")));
				}
			}
		}
//	[loop end]
		return;
	}
}

select
  min(
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      trunc(sysdate),
      (
        :pre_ccr_buffer +
        (:total_touch_time / 60 / wc.average_capacity) +
        :post_ccr_buffer
      )
    )
  ) best_end_date
from finiteload.free_capacity fc
join ifsapp.work_center wc
  on fc.contract = wc.contract
  and fc.work_center_no = wc.work_center_no
where fc.contract = :contract
  and fc.work_center_no = :work_center_no
  and ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      trunc(sysdate),
      :pre_ccr_buffer
    ) >= fc.start_work_day
  and ifsapp.work_time_calendar_api.get_end_date(
    wc.calendar_id,
    trunc(sysdate),
    :pre_ccr_buffer + (:total_touch_time / 60 / wc.average_capacity)
  ) <= fc.finish_work_day

select
  nvl(
    ifsapp.work_time_calendar_api.get_work_days_between(
      wc.calendar_id,
      ifsapp.work_time_calendar_api.get_end_date(
        wc.calendar_id,
        nvl(:start_date, trunc(sysdate)),
        :pre_wc_buffer
      ),
      fc.start_work_day
    ),
    0
  ) queue,
  greatest(
    fc.start_work_day,
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      nvl(:start_date, trunc(sysdate)),
      :pre_wc_buffer
    )
  ) load_date,
  (
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      ifsapp.work_time_calendar_api.get_end_time(
        wc.calendar_id,
        greatest(
          fc.start_work_day,
          ifsapp.work_time_calendar_api.get_end_date(
            wc.calendar_id,
            nvl(:start_date, trunc(sysdate)),
            :pre_wc_buffer
          )
        ),
        :total_touch_time
      ),
      :post_wc_buffer
    )
  ) end_date
from finiteload.free_capacity fc
join ifsapp.work_center wc
  on fc.contract = wc.contract
  and fc.work_center_no = wc.work_center_no
where fc.contract = 'LPE'
  and fc.work_center_no = :work_center
  and fc.capacity_available >= :total_touch_time
  and fc.finish_work_day >=
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      nvl(:start_date, trunc(sysdate)),
      :pre_wc_buffer
    )
  and ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      nvl(:start_date, trunc(sysdate)),
      :pre_wc_buffer
    ) <=
    ifsapp.work_time_calendar_api.get_start_time(
      wc.calendar_id,
      fc.finish_work_day,
      :total_touch_time
    )
order by
  end_date

select
  ifsapp.work_time_calendar_api.get_end_date(
    ifsapp.site_api.get_manuf_calendar_id('LPE'),
    nvl(:start_date, trunc(sysdate)),
    :duration
  ) end_date
from dual

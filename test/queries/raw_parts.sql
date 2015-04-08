select
  part_no id
from ifsapp.inventory_part sample(10)
where type_code_db = '3'
  and part_status = 'A'
order by
  dbms_random.value

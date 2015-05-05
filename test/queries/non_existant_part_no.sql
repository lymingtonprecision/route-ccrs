select
  (
    '100' ||
    (to_number(substr(max(ip.part_no), 4, 6)) + 1) ||
    'R99'
  ) id
from ifsapp.inventory_part ip

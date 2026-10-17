#!/usr/bin/env bash
# Live walkthrough of the six required cases against the running service (curl), followed by
# the MySQL query that shows the stored password is a hash.
#   ./run-evidence.sh            (service must be running on $BASE, MySQL container up)
# Uses a throwaway account and password that are fine to show in screenshots.
set -uo pipefail

BASE="${BASE:-http://localhost:8081}"
EMAIL="demo.player@example.com"
PASS="Demo-Throwaway-42"

hr()   { printf '\n\033[1;36m==== %s ====\033[0m\n' "$1"; }
call() { # method path [body] [auth-header]
  local args=(-s -i -X "$1" "$BASE$2" -H 'Content-Type: application/json')
  [[ -n "${3:-}" ]] && args+=(-d "$3")
  [[ -n "${4:-}" ]] && args+=(-H "$4")
  local disp="curl -X $1 $BASE$2"
  [[ -n "${3:-}" ]] && disp+=" -d '$3'"
  if [[ -n "${4:-}" ]]; then
    local h="$4"; [[ ${#h} -gt 60 ]] && h="${h:0:60}..."
    disp+=" -H '$h'"
  fi
  printf '\033[1;33m$ %s\033[0m\n' "$disp"
  curl "${args[@]}" | grep -v -i -E '^(vary|cache-control|pragma|expires|keep-alive|connection|date|transfer-encoding):'
  echo
}

hr "1. Register a new user (expect 201)"
call POST /api/auth/register "{\"email\":\"$EMAIL\",\"displayName\":\"Demo Player\",\"password\":\"$PASS\"}"

hr "2. Register the same email again (expect 409)"
call POST /api/auth/register "{\"email\":\"$EMAIL\",\"displayName\":\"Copycat\",\"password\":\"$PASS\"}"

hr "2b. Register with invalid input (expect 400)"
call POST /api/auth/register '{"email":"not-an-email","displayName":"","password":"short"}'

hr "3. Login with correct credentials (expect 200 + token)"
LOGIN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
call POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}"
TOKEN=$(printf '%s' "$LOGIN" | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
echo "Decoded token payload (Base64URL, readable by anyone - which is why it holds no PII):"
printf '%s' "$TOKEN" | cut -d. -f2 | python3 -c 'import sys,base64,json,datetime as d
s=sys.stdin.read().strip(); p=json.loads(base64.urlsafe_b64decode(s+"="*(-len(s)%4)))
print(json.dumps(p,indent=2)); print("exp - iat =",p["exp"]-p["iat"],"seconds; expires",d.datetime.fromtimestamp(p["exp"],d.timezone.utc).isoformat())'

hr "4. Login with the wrong password (expect 401, generic message)"
call POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"Wrong-Password-1\"}"

hr "4b. Login with an unregistered email (expect 401, SAME message)"
call POST /api/auth/login "{\"email\":\"nobody@example.com\",\"password\":\"$PASS\"}"

hr "5. GET /api/users/me with the valid token (expect 200)"
call GET /api/users/me "" "Authorization: Bearer $TOKEN"

hr "6. GET /api/users/me with no token (expect 401)"
call GET /api/users/me

hr "6b. GET /api/users/me with a tampered token - role changed to ADMIN, signature kept (expect 401)"
TAMPERED=$(python3 - "$TOKEN" <<'PY'
import sys,base64
h,p,s=sys.argv[1].split(".")
raw=base64.urlsafe_b64decode(p+"="*(-len(p)%4)).decode().replace('"USER"','"ADMIN"')
print(h+"."+base64.urlsafe_b64encode(raw.encode()).decode().rstrip("=")+"."+s)
PY
)
call GET /api/users/me "" "Authorization: Bearer $TAMPERED"

hr "7. MySQL: SELECT id, email, password_hash FROM users;"
set -a; [[ -f .env ]] && source .env; set +a
docker exec gameflix-mysql mysql -ugameflix_app -p"$DB_PASSWORD" gameflix -t \
  -e "SELECT id, email, password_hash, LENGTH(password_hash) AS len FROM users;" 2>/dev/null

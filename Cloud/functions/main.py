from firebase_functions import db_fn, https_fn, options
from firebase_admin import initialize_app, db

# Initialize the Firebase Admin SDK
initialize_app()

# CRITICAL: This must match your Android App's region: "europe-west1"
options.set_global_options(region="europe-west1", max_instances=1)

# --- 1. Automatic Analysis (Triggers on new data) ---
@db_fn.on_value_created(reference="/somnosense/data/{pushId}")
def analyze_sensor_data(event: db_fn.Event[object]):
    new_data = event.data
    push_id = event.params["pushId"]
    
    if not new_data:
        return

    # Basic logic to alert on CO levels
    gas_data = new_data.get("gas", {})
    co_level = gas_data.get("co", 0)
    
    status = "Healthy"
    if co_level > 50.0:
        status = "Danger"

    # Write analysis to the database
    db.reference(f"/somnosense/analysis/{push_id}").set({
        "status": status,
        "analyzed_at": {".sv": "timestamp"}
    })

# --- 2. On-Demand Analysis (Called by AnalysisActivity.kt) ---
@https_fn.on_call()
def calculate_sleep_score_on_demand(req: https_fn.CallableRequest):
    """
    This function is called by: 
    functions.getHttpsCallable("calculate_sleep_score_on_demand").call()

    Modified to compute a per-record Sleep Comfort Index (SCI) using:
      - temperature, CO2 (or CO), humidity, and noise
    The per-record SCI is 0..10 and the returned "score" is the mean SCI scaled to 0..100.
    """
    try:
        # Fetch last 1000 records from the same path the App uses
        ref = db.reference("somnosense/data")
        snapshot = ref.order_by_key().limit_to_last(1000).get()
        
        if not snapshot or not isinstance(snapshot, dict):
            return {
                "score": 0, "avg_temp": 0, "avg_hum": 0, "avg_co": 0, "avg_sound": 0
            }

        temps, hums, cos, sounds = [], [], [], []
        per_indexes = []
        
        for key in snapshot:
            val = snapshot[key]
            env = val.get("environment", {})
            gas = val.get("gas", {})
            
            temp = env.get("temp", 0)
            hum = env.get("hum", 0)
            co = gas.get("co2", 0)

            sound_count = val.get("sound_count")

            temps.append(temp)
            hums.append(hum)
            cos.append(co)
            sounds.append(sound_count)


            # --- 1. TEMPERATURE SCORE (Max 10) ---
            if 18 <= temp <= 22:
                s_temp = 10
            elif temp < 18:
                s_temp = max(0, 10 - (18 - temp) * 2)
            else:
                s_temp = max(0, 10 - (temp - 22) * 1.5)

            # --- 2. CO2 SCORE (Max 10) ---
            # Ideal CO2 ~6.5 ppm; penalize above that linearly
            s_co = max(0, 10 - max(0, (co - 6.5)) * 0.01)

            # --- 3. SOUND SCORE (Max 10) ---
            # each extra event reduces 1 point
            s_sound = max(0, 10 - sound_count * 1)

            # --- 4. HUMIDITY SCORE (Max 10) ---
            if 40 <= hum <= 60:
                s_hum = 10
            else:
                distancia = min(abs(hum - 40), abs(hum - 60))
                s_hum = max(0, 10 - distancia * 0.5)

            # Weighted final (0..10)
            # Weights: temperature 40%, CO2 30%, humidity 20%, sound 10%
            index = (s_temp * 0.40) + (s_co * 0.30) + (s_hum * 0.20) + (s_sound * 0.10)
            per_indexes.append(index)

        count = len(temps)
        if count == 0:
            return {"score": 0, "avg_temp": 0, "avg_hum": 0, "avg_co": 0, "avg_sound": 0}

        avg_t = sum(temps) / count
        avg_h = sum(hums) / count
        avg_c = sum(cos) / count
        avg_s = sum(sounds) / count
        mean_index = sum(per_indexes) / count if per_indexes else 0

        # Scale 0..10 to 0..100 to keep the original external contract
        score = max(0, min(100, int(mean_index * 10)))

        # Return format matches AnalysisActivity.kt: data["score"], etc.
        return {
            "score": score,
            "avg_temp": round(avg_t, 1),
            "avg_hum": round(avg_h, 1),
            "avg_co": round(avg_c, 2),
            "avg_sound": round(avg_s, 1),
            "sci": round(mean_index, 2)
        }
        
    except Exception as e:
        print(f"Error processing analysis: {str(e)}")
        raise https_fn.HttpsError(
            code=https_fn.FunctionsErrorCode.INTERNAL,
            message=str(e)
        )
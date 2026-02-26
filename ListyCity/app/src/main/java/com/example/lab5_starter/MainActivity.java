package com.example.lab5_starter;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CityDialogFragment.CityDialogListener {

    private Button addCityButton;
    private ListView cityListView;

    private ArrayList<City> cityArrayList;
    private ArrayAdapter<City> cityArrayAdapter;

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference citiesRef;

    // Swipe-to-delete
    private GestureDetector gestureDetector;
    private static final int SWIPE_THRESHOLD = 120;
    private static final int SWIPE_VELOCITY_THRESHOLD = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set views
        addCityButton = findViewById(R.id.buttonAddCity);
        cityListView = findViewById(R.id.listviewCities);

        // create city array
        cityArrayList = new ArrayList<>();
        cityArrayAdapter = new CityArrayAdapter(this, cityArrayList);
        cityListView.setAdapter(cityArrayAdapter);

        // Firestore init
        db = FirebaseFirestore.getInstance();
        citiesRef = db.collection("cities");

        // Snapshot listener
        citiesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Toast.makeText(this, "Error loading cities: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (value == null) return;

            cityArrayList.clear();
            for (QueryDocumentSnapshot doc : value) {
                City city = doc.toObject(City.class);
                // Safety: if a doc somehow lacks name, use doc id
                if (city.getName() == null || city.getName().trim().isEmpty()) {
                    city.setName(doc.getId());
                }
                cityArrayList.add(city);
            }
            cityArrayAdapter.notifyDataSetChanged();
        });

        // Small hint message
        Toast.makeText(this, "Tip: Swipe left on a city to delete", Toast.LENGTH_LONG).show();

        // Add city dialog
        addCityButton.setOnClickListener(view -> {
            CityDialogFragment cityDialogFragment = new CityDialogFragment();
            cityDialogFragment.show(getSupportFragmentManager(), "Add City");
        });

        // Edit city dialog (tap)
        cityListView.setOnItemClickListener((adapterView, view, i, l) -> {
            City city = cityArrayAdapter.getItem(i);
            CityDialogFragment cityDialogFragment = CityDialogFragment.newInstance(city);
            cityDialogFragment.show(getSupportFragmentManager(), "City Details");
        });

        // Swipe-left-to-delete on ListView items
        setupSwipeToDelete();
    }

    private void setupSwipeToDelete() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                    // Left swipe = delete
                    if (diffX < 0) {
                        int position = cityListView.pointToPosition((int) e1.getX(), (int) e1.getY());
                        if (position != ListView.INVALID_POSITION) {
                            City city = cityArrayAdapter.getItem(position);
                            if (city != null) {
                                deleteCityFromFirestore(city);
                                Toast.makeText(MainActivity.this,
                                        "Deleted: " + city.getName(),
                                        Toast.LENGTH_SHORT).show();
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });

        cityListView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void deleteCityFromFirestore(City city) {
        String docId = city.getName();
        if (docId == null || docId.trim().isEmpty()) return;

        citiesRef.document(docId).delete()
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    @Override
    public void updateCity(City city, String title, String year) {
        if (city == null) return;

        String oldName = city.getName();

        city.setName(title);
        city.setProvince(year);

        if (oldName != null && !oldName.equals(title)) {
            citiesRef.document(oldName).delete();
        }

        if (title != null && !title.trim().isEmpty()) {
            citiesRef.document(title).set(city)
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }
    }

    @Override
    public void addCity(City city) {
        if (city == null) return;
        if (city.getName() == null || city.getName().trim().isEmpty()) {
            Toast.makeText(this, "City name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        citiesRef.document(city.getName()).set(city)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Add failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
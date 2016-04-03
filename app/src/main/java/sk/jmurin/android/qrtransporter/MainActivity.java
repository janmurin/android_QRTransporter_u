package sk.jmurin.android.qrtransporter;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import sk.jmurin.android.qrtransporter.decoding.ReadQRActivity;
import sk.jmurin.android.qrtransporter.sending.OdoslatActivity;
import sk.jmurin.android.qrtransporter.sending.OdoslatActivity2;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void odoslatButtonClicked(View view){
        System.out.println("odoslatButtonClicked");
        //Toast.makeText(this, "odoslatButtonClicked", Toast.LENGTH_SHORT).show();
        Intent odoslat = new Intent(this, OdoslatActivity.class);
        startActivity(odoslat);
    }

    public void prijatButtonClicked(View view){
        System.out.println("prijatButtonClicked");
        Intent odoslat = new Intent(this, ReadQRActivity.class);
        odoslat.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(odoslat);
    }
    public void odoslatButtonClicked2(View view){
        System.out.println("odoslatButtonClicked2");
        //Toast.makeText(this, "odoslatButtonClicked", Toast.LENGTH_SHORT).show();
        Intent odoslat = new Intent(this, OdoslatActivity2.class);
        startActivity(odoslat);
    }

    public void prijatButtonClicked2(View view){
        System.out.println("prijatButtonClicked2");
        Toast.makeText(this,"not yet implemented",Toast.LENGTH_SHORT).show();
//        Intent odoslat = new Intent(this, ReadQRActivity.class);
//        odoslat.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
//        startActivity(odoslat);
    }
}

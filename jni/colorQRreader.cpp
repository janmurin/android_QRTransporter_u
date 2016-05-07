#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <iostream>
#include <cmath>
#include <map>
#include <set>
#include <queue>
#include <android/log.h>

#define LOG_TAG "ColorQRAnalyzer"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

using namespace cv;
using namespace std;

int getCislo() {
    return 23;
}

extern "C" {
JNIEXPORT jstring

JNICALL Java_sk_jmurin_android_qrtransporter_decoding_ColorQRAnalyzer_getMessage
        (JNIEnv *env, jobject thisObj, jlong matAddrRgba) {

    Mat &mRgb = *(Mat *) matAddrRgba;
    Mat gray(mRgb.size(), CV_MAKETYPE(mRgb.depth(), 1)); // To hold Grayscale Image
    Mat edges(mRgb.size(), CV_MAKETYPE(mRgb.depth(), 1));
    vector <vector<Point> > contours;
    vector <Vec4i> hierarchy;

    // 0. finding contours and mass centers
    cvtColor(mRgb, gray, CV_RGB2GRAY); // Convert Image captured from Image Input to GrayScale
    Canny(gray, edges, 100, 200, 3); // Apply Canny edge detection on the gray image

    findContours(edges, contours, hierarchy, RETR_TREE,
                 CHAIN_APPROX_SIMPLE); // Find contours with hierarchy
    std::stringstream sstm;
    sstm << "contours size: " << contours.size();
    string result = sstm.str();
    return env->NewStringUTF(result.c_str());

}

const int CV_QR_NORTH = 0;
const int CV_QR_EAST = 1;
const int CV_QR_SOUTH = 2;
const int CV_QR_WEST = 3;

double cv_distance(Point2f P, Point2f Q); // Get Distance between two points
float cv_lineEquation(Point2f L, Point2f M,
                      Point2f J); // Perpendicular Distance of a Point J from line formed by Points L and M; Solution to equation of the line Val = ax+by+c
float cv_lineSlope(Point2f L, Point2f M,
                   int &alignement); // Slope of a line by two Points L and M on it; Slope of line, S = (x1 -x2) / (y1- y2)
void cv_getVertices(vector < vector < Point > > contours, int
c_id , float slope, vector<Point2f> & X ) ;
void cv_updateCorner(Point2f P, Point2f ref, float &baseline, Point2f &corner);
void cv_updateCornerOr(int orientation, vector <Point2f> IN, vector <Point2f> &OUT);
bool getIntersectionPoint(Point2f a1, Point2f a2, Point2f b1, Point2f b2, Point2f &intersection, Mat &image, Mat &gray);
float cross(Point2f v1, Point2f v2);
int drawVertices(Mat img, vector <Point2f> L, vector <Point2f> M, vector <Point2f> O);
void initKlasifikator();
std::set <int> navstiveneSet;
int VSETKYCH = -1;
int rowsHint = -1;
int smery[24][2] = {
        {1,  1},
        {1,  -1},
        {-1, 1},
        {-1, -1},
        {0,  1},
        {1,  0},
        {0,  -1},
        {-1, 0},
        {0,  -2},
        {1,  -2},
        {2,  -2},
        {2,  -1},
        {2,  0},
        {2,  1},
        {2,  2},
        {1,  2},
        {0,  2},
        {-1, 2},
        {-2, 2},
        {-2, 1},
        {-2, 0},
        {-2, -1},
        {-2, -2},
        {-1, -2}
};
string getBitsFromIntColor(int f) {
    string out;
    switch (f) {
        case -16777216:
            out = "000";
            break;
        case -65536:
            out = "100";
            break;
        case -16711936:
            out = "010";
            break;
        case -256:
            out = "110";
            break;
        case -16776961:
            out = "001";
            break;
        case -65281:
            out = "101";
            break;
        case -16711681:
            out = "011";
            break;
        case -1:
            out = "111";
            break;
            //        case -12566464:out = "SUM";
            //            break;
        default: //cout << "CHYBNE CISLO: " << f << " " << b << " " << g << " " << r << endl;
            out = "ERROR";
    }
    return out;
}
double cv_distance(Point2f P, Point2f Q) {
    return sqrt(pow(abs(P.x - Q.x), 2) + pow(abs(P.y - Q.y), 2));
}
int hashCode(char a[], int size) {
    if (size == 0) {
        return 0;
    }
    int result = 1;
    for (int i = 0; i < size; i++)
        result = 31 * result + a[i];

    return result;
}
void readDataFromQR(int *data, Mat img, int ROWS, int COLUMNS, double elementSize) {
    int MARKER_SIZE = 8;
    int CORNER_MARKER_SIZE = 2;
    int count = 0;
    int MINUS = elementSize / 3;
    for (int y = 0; y < ROWS; y++) {
        for (int x = 0; x < COLUMNS; x++) {
            if ((x < MARKER_SIZE && y < MARKER_SIZE) // skip left top
                || (x < MARKER_SIZE && y >= ROWS - MARKER_SIZE) // skip left bottom
                || (x >= COLUMNS - MARKER_SIZE && y < MARKER_SIZE) // skip right top
                || (x >= COLUMNS - CORNER_MARKER_SIZE && y >= ROWS - CORNER_MARKER_SIZE)) { // skip corner bottom
                continue;
            }
            if (count < 6) {
                count++;
                continue; // skipping 6 colors
            }
            int b = 0, g = 0, r = 0;
            float area = 0;
            // spriemernime farby vo stvorceku
            for (int yy = y * elementSize + MINUS; yy < y * elementSize + elementSize - MINUS; yy++) {
                Vec3b *row = img.ptr<Vec3b>(yy);
                for (int xx = x * elementSize + MINUS; xx < x * elementSize + elementSize - MINUS; xx++) {
                    b += (int) row[xx][0];
                    g += (int) row[xx][1];
                    r += (int) row[xx][2];
                    area++;
                }
            }
            b = b / area;
            g = g / area;
            r = r / area;
            // zaokruhlime farbu
            r = (int) (round(r / 16.0) * 16);
            g = (int) (round(g / 16.0) * 16);
            b = (int) (round(b / 16.0) * 16);
            // zakodujeme do intu
            int f = 0;
            r = min(r, 255);
            f |= r << 16;
            g = min(g, 255);
            f |= g << 8;
            b = min(b, 255);
            f |= b;

            data[count - 6] = f; // ulozime farbu do pola, -6 lebo sme skipli 6 farieb
            count++;
        }
    }
}
// Function: Perpendicular Distance of a Point J from line formed by Points L and M; Equation of the line ax+by+c=0
// Description: Given 3 points, the function derives the line quation of the first two points,
//	  calculates and returns the perpendicular distance of the the 3rd point from this line.

float cv_lineEquation(Point2f L, Point2f M, Point2f J) {
    float a, b, c, pdist;

    a = -((M.y - L.y) / (M.x - L.x));
    b = 1.0;
    c = (((M.y - L.y) / (M.x - L.x)) * L.x) - L.y;

    // Now that we have a, b, c from the equation ax + by + c, time to substitute (x,y) by values from the Point J

    pdist = (a * J.x + (b * J.y) + c) / sqrt((a * a) + (b * b));
    return pdist;
}


// Function: Slope of a line by two Points L and M on it; Slope of line, S = (x1 -x2) / (y1- y2)
// Description: Function returns the slope of the line formed by given 2 points, the alignement flag
//	  indicates the line is vertical and the slope is infinity.

float cv_lineSlope(Point2f L, Point2f M, int &alignement) {
    float dx, dy;
    dx = M.x - L.x;
    dy = M.y - L.y;

    if (dy != 0) {
        alignement = 1;
        return (dy / dx);
    } else // Make sure we are not dividing by zero; so use 'alignement' flag
    {
        alignement = 0;
        return 0.0;
    }
}


// Function: Routine to calculate 4 Corners of the Marker in Image Space using Region partitioning
// Theory: OpenCV Contours stores all points that describe it and these points lie the perimeter of the polygon.
//	The below function chooses the farthest points of the polygon since they form the vertices of that polygon,
//	exactly the points we are looking for. To choose the farthest point, the polygon is divided/partitioned into
//	4 regions equal regions using bounding box. Distance algorithm is applied between the centre of bounding box
//	every contour point in that region, the farthest point is deemed as the vertex of that region. Calculating
//	for all 4 regions we obtain the 4 corners of the polygon ( - quadrilateral).
void cv_getVertices(vector < vector < Point > > contours, int
c_id , float slope, vector<Point2f> & quad ) {
Rect box;
box = boundingRect(contours[c_id]);

Point2f M0, M1, M2, M3;
Point2f A, B, C, D, W, X, Y, Z;

A = box.tl();
B . x = box.br().x;
B . y = box.tl().y;
C = box.br();
D . x = box.tl().x;
D . y = box.br().y;


W . x = (A.x + B.x) / 2;
W . y = A.y;

X . x = B.x;
X . y = (B.y + C.y) / 2;

Y . x = (C.x + D.x) / 2;
Y . y = C.y;

Z . x = D.x;
Z . y = (D.y + A.y) / 2;

float dmax[4];
dmax [ 0 ] = 0.0 ;
dmax [ 1 ] = 0.0 ;
dmax [ 2 ] = 0.0 ;
dmax [ 3 ] = 0.0 ;

float pd1 = 0.0;
float pd2 = 0.0;

if ( slope > 5 || slope < - 5 ) {

for ( int i = 0;
i<contours[c_id] . size();
i ++ ) {
pd1 = cv_lineEquation(C, A, contours[c_id][i]); // Position of point w.r.t the diagonal AC
pd2 = cv_lineEquation(B, D, contours[c_id][i]); // Position of point w.r.t the diagonal BD

if (( pd1 >= 0.0 ) && ( pd2 > 0.0 )) {
cv_updateCorner(contours[c_id][i], W, dmax[1], M1 ) ;
} else if ((pd1 > 0.0) && (pd2 <= 0.0)) {
cv_updateCorner(contours[c_id][i], X, dmax[2], M2
);
} else if ((pd1 <= 0.0) && (pd2 < 0.0)) {
cv_updateCorner(contours[c_id][i], Y, dmax[3], M3
);
} else if ((pd1 < 0.0) && (pd2 >= 0.0)) {
cv_updateCorner(contours[c_id][i], Z, dmax[0], M0
);
} else
continue;
}
} else {
int halfx = (A.x + B.x) / 2;
int halfy = (A.y + D.y) / 2;

for (
int i = 0;
i<contours[c_id].

size();

i++) {
if ((contours[c_id][i].x<halfx) && (contours[c_id][i].y <= halfy)) {
cv_updateCorner(contours[c_id][i], C, dmax[2], M0
);
} else if ((contours[c_id][i].x >= halfx) && (contours[c_id][i].y<halfy)) {
cv_updateCorner(contours[c_id][i], D, dmax[3], M1
);
} else if ((contours[c_id][i].x > halfx) && (contours[c_id][i].y >= halfy)) {
cv_updateCorner(contours[c_id][i], A, dmax[0], M2
);
} else if ((contours[c_id][i].x <= halfx) && (contours[c_id][i].y > halfy)) {
cv_updateCorner(contours[c_id][i], B, dmax[1], M3
);
}
}
}

quad.
push_back(M0);
quad.
push_back(M1);
quad.
push_back(M2);
quad.
push_back(M3);

}

bool isBlack(Mat gray, Mat img, int x, int y) {
   // LOGD("isBlack: x=%d y=%d cols=%d rows=%d", x,y,gray.cols,gray.rows);
    if(x>=gray.cols || x<0 || y>=gray.rows || y<0){
        LOGD("isBlack: return false");
        return false;
    }
    //cout << "isBlack " << x << " " << y;
    uchar *row = gray.ptr<uchar>(y);
    int bb = row[x];
    // cout << " bb: " << bb << endl;
    if (bb > 150) {
        return true;
    } else {
        //img.at<Vec3b>(y, x) = Vec3b(0, 255, 255);
        return false;
    }
}

long double getDistance(Point2f bod, Point2f a, Point2f b) {
    Point2f v1 = bod - a;
    Point2f v2 = bod - b;
    return std::sqrt(static_cast<long double> (v1.x * v1.x + v1.y * v1.y)) +
           std::sqrt(static_cast<long double> (v2.x * v2.x + v2.y * v2.y));
    // return abs(bod.x - a.x) + abs(bod.y - a.y) + abs(bod.x - b.x) + abs(bod.y - b.y);
    //long double s=std::sqrt(static_cast<long double>(3));
}

int getIntFromPos(int x, int y) {
    // x a y nesmie byt > 65535
    int posint = 0;
    posint |= (x & 0x0000FFFF) << 16;
    posint |= (y & 0x0000FFFF);
    return posint;
}

// Function: Get the Intersection Point of the lines formed by sets of two points

bool getIntersectionPoint(Point2f a1, Point2f a2, Point2f b1, Point2f b2, Point2f &intersection, Mat &image, Mat &gray) {
    LOGD("getIntersectionPoint: a1=(%.2f,%.2f) a2=(%.2f,%.2f) b1=(%.2f,%.2f) b2=(%.2f,%.2f) ", a1.x,a1.y,a2.x,a2.y,b1.x,b1.y,b2.x,b2.y);
    Point2f p = a1;
    Point2f q = b1;
    Point2f r(a2 - a1);
    Point2f s(b2 - b1);

    if (cross(r, s) == 0) {
        return false;
    }

    float t = cross(q - p, s) / cross(r, s);
    intersection = p + t * r;
    // cout << "intersection bod pos: " << intersection.x << " " << intersection.y << endl;

    // riesenie 2: od priesecnika sa posuvame doprava a dole kym je cierna
    //Point2f v=s+r;


    if (!isBlack(gray, image, intersection.x, intersection.y)) {
        float sl = sqrt(s.x * s.x + s.y * s.y); // dlzka s vektora aby sme sa vedeli nejako pomaly posuvat
        float rl = sqrt(r.x * r.x + r.y * r.y); // dlzka r vektora
        LOGD("!isBlack: sl=%.2f rl=%.2f", sl,rl);
        //cout << "priesecnik nie je biely, hladame cierny bod!!!" << endl;
        //circle(image, intersection, 1, Scalar(0, 0, 255), 1, 8, 0);
        // skusame rozne vzdialenosti
        for (int dist = 5; dist <= 30; dist += 5) {
            Point2f pom = intersection - dist * s / sl;
            // ak sme sa netrafili do cierneho bodu, tak ho hladame najprv vlavo, hore a potom vlavohore
            if (isBlack(gray, image, pom.x, pom.y)) { // skusime vlavo
                intersection = Point2f(pom.x, pom.y);
                break;
            } else {
                pom = intersection - dist * r / rl;
                if (isBlack(gray, image, pom.x, pom.y)) { // skusime hore
                    intersection = Point2f(pom.x, pom.y);
                    break;
                } else {
                    pom = intersection - dist * s / sl;
                    pom = pom - dist * r / rl;
                    if (isBlack(gray, image, pom.x, pom.y)) { // skusime vlavohore
                        intersection = Point2f(pom.x, pom.y);
                        break;
                    }
                }
            }
        }
    }

    //
    //    Point2f pom = intersection + (s + r); // vektor od priesecnika von
    //    //    cout<<"intersection: "<<intersection.x<<"x"<<intersection.y<<endl;
    //    //    cout<<"pom: "<<pom.x<<"x"<<pom.y<<endl;
    //    line(image, intersection, pom, Scalar(0, 255, 255), 1, 8, 0); // na debug ucely
    //    circle(image, intersection, 1, Scalar(0, 255, 255), 1, 8, 0);

    Point2f maxBod = intersection;

    // algoritmus vlny dokym su cierne body
    Point2i prvy = Point2i(round(maxBod.x), round(maxBod.y));
    queue <Point2i> zoznam;
    zoznam.push(prvy); // algoritmus vlny kym najdeme suseda co je dalej ako sme my
    navstiveneSet.clear();
    navstiveneSet.insert(getIntFromPos(prvy.x, prvy.y));
    int surX, surY;
    int pushed = 0;
    long double maxDist = getDistance(prvy, a1, b1);
    long double d = 0;
    int maxX = prvy.x;
    int maxY = prvy.y;
    while (!zoznam.empty()) {
        //waitKey(0);
        //cout << "popping " << zoznam.front().x << " " << zoznam.front().y << endl;
        //image.at<Vec3b>(zoznam.front().y, zoznam.front().x) = Vec3b(0, 0, 255);
        //cout<<"vybraty: "<<zoznam.front().x<<","<<zoznam.front().y<<endl;
        for (int i = 0; i < 24; i++) {
            // pridame jeho susedov z 8 smerov
            surX = zoznam.front().x + smery[i][0];
            surY = zoznam.front().y + smery[i][1];
            //cout << "navstivene? " << surX << " " << surY << ": " << navstivene[surY][surX] << " false? " << (navstivene[surY][surX] == false) << endl;
            if (navstiveneSet.find(getIntFromPos(surX, surY)) == navstiveneSet.end()
                && isBlack(gray, image, surX, surY)) {
                d = getDistance(Point2f(surX, surY), a1, b1);
                if (d >= maxDist) {
                    maxX = surX;
                    maxY = surY;
                    maxDist = d;
                    pushed++;
                    //cout << "pushing " << surX << " " << surY << " pushed: " << pushed << endl;
                    zoznam.push(Point2i(surX, surY));
                }
                navstiveneSet.insert(getIntFromPos(surX, surY));
            }
        }
        zoznam.pop();
        if (pushed > 300) {
            cout << "pushed>300, breaking from while loop" << endl;
            break;
        }
    }
    //image.at<Vec3b>(Point(maxX, maxY)) = Vec3b(0, 200, 200);
    // cout << "zoznam max pos: " << maxX << " " << maxY << " max dist: " << maxDist << endl;
    maxBod = Point2f(maxX, maxY);

    intersection = Point2f(maxBod.x, maxBod.y);
    //circle(image, intersection, 2, Scalar(255, 255, 255), 1, 8, 0);
    return true;
}

float cross(Point2f v1, Point2f v2) {
    return v1.x * v2.y - v1.y * v2.x;
}


// Function: Compare a point if it more far than previously recorded farthest distance
// Description: Farthest Point detection using reference point and baseline distance

void cv_updateCorner(Point2f P, Point2f ref, float &baseline, Point2f &corner) {
    float temp_dist;
    temp_dist = cv_distance(P, ref);

    if (temp_dist > baseline) {
        baseline = temp_dist; // The farthest distance is the new baseline
        corner = P; // P is now the farthest point
    }

}


// Function: Sequence the Corners wrt to the orientation of the QR Code

void cv_updateCornerOr(int orientation, vector <Point2f> IN, vector <Point2f> &OUT) {
    Point2f M0, M1, M2, M3;
    if (orientation == CV_QR_NORTH) {
        M0 = IN[0];
        M1 = IN[1];
        M2 = IN[2];
        M3 = IN[3];
    } else if (orientation == CV_QR_EAST) {
        M0 = IN[1];
        M1 = IN[2];
        M2 = IN[3];
        M3 = IN[0];
    } else if (orientation == CV_QR_SOUTH) {
        M0 = IN[2];
        M1 = IN[3];
        M2 = IN[0];
        M3 = IN[1];
    } else if (orientation == CV_QR_WEST) {
        M0 = IN[3];
        M1 = IN[0];
        M2 = IN[1];
        M3 = IN[2];
    }

    OUT.push_back(M0);
    OUT.push_back(M1);
    OUT.push_back(M2);
    OUT.push_back(M3);
}
string getFarba(char bity[]) {
    string out = "www";
    if (bity[0] == '0' && bity[1] == '0' && bity[2] == '0') {
        out = "BLACK";
    }
    if (bity[0] == '0' && bity[1] == '0' && bity[2] == '1') {
        out = "RED";
    }
    if (bity[0] == '0' && bity[1] == '1' && bity[2] == '0') {
        out = "GREEN";
    }
    if (bity[0] == '0' && bity[1] == '1' && bity[2] == '1') {
        out = "YELLOW";
    }
    if (bity[0] == '1' && bity[1] == '0' && bity[2] == '0') {
        out = "BLUE";
    }
    if (bity[0] == '1' && bity[1] == '0' && bity[2] == '1') {
        out = "MAGENTA";
    }
    if (bity[0] == '1' && bity[1] == '1' && bity[2] == '0') {
        out = "CYAN";
    }
    if (bity[0] == '1' && bity[1] == '1' && bity[2] == '1') {
        out = "WHITE";
    }

    return out;
}

JNIEXPORT jstring

JNICALL Java_sk_jmurin_android_qrtransporter_decoding_ColorQRAnalyzer_readQR
        (JNIEnv *env, jobject thisObj, jlong matAddrRgba, jobject obj) {

    jclass cls = env->GetObjectClass(obj);
    // get field [F = Array of float
    jfieldID fieldId = env->GetFieldID(cls, "klasifikator", "[I");
    // Get the object field, returns JObject (because it’s an Array)
    jobject objArray = env->GetObjectField (obj, fieldId);
    // Cast it to a jfloatarray
    jintArray* iArray = reinterpret_cast<jintArray*>(&objArray);
    // Get the elements
    int* klasifikator = env->GetIntArrayElements(*iArray, 0);
    // get the field ID of the "intValue" field of this class
    jfieldID fidInt = env->GetFieldID(cls, "rowsHint", "I");
    // get the value of the field in this instance of the above class
    jint valInt = env->GetIntField(obj, fidInt);
    rowsHint=valInt;
    fidInt = env->GetFieldID(cls, "hintID", "I");
    valInt = env->GetIntField(obj, fidInt);
    int hintID=valInt;

    std::stringstream sstm;
    Mat &image = *(Mat *) matAddrRgba;
    // Creation of Intermediate 'Image' Objects required later
    Mat gray(image.size(), CV_MAKETYPE(image.depth(), 1)); // To hold Grayscale Image
    Mat edges(image.size(), CV_MAKETYPE(image.depth(), 1)); // To hold Grayscale Image
    Mat qr_raw, thr;
    vector <vector<Point> > contours;
    vector <Vec4i> hierarchy;

    int mark, A, B, C, top, right, bottom, median1, median2, outlier;
    float AB, BC, CA, dist, slope;
    int align, orientation;
    int DBG = 1; // Debug Flag

    // 0. finding contours and mass centers
    cvtColor(image, gray, CV_RGB2GRAY); // Convert Image captured from Image Input to GrayScale
    Canny(gray, edges, 100, 200, 3); // Apply Canny edge detection on the gray image
    threshold(gray, thr, 0, 255, CV_THRESH_BINARY_INV | CV_THRESH_OTSU);
    Mat element = getStructuringElement(MORPH_CROSS, Size(3, 3));
    morphologyEx(edges, edges, MORPH_CLOSE, element);
    findContours(edges, contours, hierarchy, RETR_TREE,
                 CHAIN_APPROX_SIMPLE); // Find contours with hierarchy
    mark = 0; // Reset all detected marker count for this frame

    // Get Moments for all Contours and the mass centers
    vector <Moments> mu(contours.size());
    vector <Point2f> mc(contours.size());

    for (int i = 0; i < contours.size(); i++) {
        mu[i] = moments(contours[i], false);
        mc[i] = Point2f(mu[i].m10 / mu[i].m00, mu[i].m01 / mu[i].m00);
    }

    // 1. processing the contour data

    // Find Three repeatedly enclosed contours A,B,C
    // NOTE: 1. Contour enclosing other contours is assumed to be the three Alignment markings of the QR code.
    // 2. Alternately, the Ratio of areas of the "concentric" squares can also be used for identifying base Alignment markers.
    // The below demonstrates the first method
    vector <int> markers;
    for (int i = 0; i < contours.size(); i++) {
        int k = i;
        int c = 0;

        while (hierarchy[k][2] != -1) {
            k = hierarchy[k][2];
            c = c + 1;
        }
        if (hierarchy[k][2] != -1)
            c = c + 1;

        if (c >= 4) {
            markers.push_back(i);
        }
    }
    vector <int> children;
    for (int i = 0; i < markers.size(); i++) {
        children.push_back(hierarchy[markers[i]][2]);
    }
    vector <int> newMarkers;
    for (int i = 0; i < markers.size(); i++) {
        bool isChild = false;
        for (int j = 0; j < children.size(); j++) {
            if (markers[i] == children[j]) {
                isChild = true;
            }
        }
        if (!isChild) {
            newMarkers.push_back(markers[i]);
            //drawContours(konturyImg,contours,markers[i],Scalar(0,0,255),2);
            if (mark == 0) A = markers[i];
            else if (mark == 1)
                B = markers[i]; // i.e., A is already found, assign current contour to B
            else if (mark == 2)
                C = markers[i]; // i.e., A and B are already found, assign current contour to C*/
            mark++;
        }
    }
    sstm << "markov: " << newMarkers.size() << endl;
    if (mark < 3){
        env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
        string result = sstm.str();
        return env->NewStringUTF(result.c_str());
    }else{ // Ensure we have (atleast 3; namely A,B,C) 'Alignment Markers' discovered
        // We have found the 3 markers for the QR code; Now we need to determine which of them are 'top', 'right' and 'bottom' markers

        // Determining the 'top' marker
        // Vertex of the triangle NOT involved in the longest side is the 'outlier'
        AB = cv_distance(mc[A], mc[B]);
        BC = cv_distance(mc[B], mc[C]);
        CA = cv_distance(mc[C], mc[A]);

        if (AB > BC && AB > CA) {
            outlier = C;
            median1 = A;
            median2 = B;
        } else if (CA > AB && CA > BC) {
            outlier = B;
            median1 = A;
            median2 = C;
        } else if (BC > AB && BC > CA) {
            outlier = A;
            median1 = B;
            median2 = C;
        }

        top = outlier; // The obvious choice

        dist = cv_lineEquation(mc[median1], mc[median2],
                               mc[outlier]); // Get the Perpendicular distance of the outlier from the longest side
        slope = cv_lineSlope(mc[median1], mc[median2], align); // Also calculate the slope of the longest side

        // Now that we have the orientation of the line formed median1 & median2 and we also have the position of the outlier w.r.t. the line
        // Determine the 'right' and 'bottom' markers

        if (align == 0) {
            bottom = median1;
            right = median2;
        } else if (slope < 0 && dist < 0) { // Orientation - North
            bottom = median1;
            right = median2;
            orientation = CV_QR_NORTH;
        } else if (slope > 0 && dist < 0) { // Orientation - East
            right = median1;
            bottom = median2;
            orientation = CV_QR_EAST;
        } else if (slope < 0 && dist > 0) { // Orientation - South
            right = median1;
            bottom = median2;
            orientation = CV_QR_SOUTH;
        } else if (slope > 0 && dist > 0) { // Orientation - West
            bottom = median1;
            right = median2;
            orientation = CV_QR_WEST;
        }


        // To ensure any unintended values do not sneak up when QR code is not present
        float area_top, area_right, area_bottom;

        if (top < contours.size() && right < contours.size() && bottom < contours.size()
            && contourArea(contours[top]) > 10 && contourArea(contours[right]) > 10 && contourArea(contours[bottom]) > 10) {

            vector <Point2f> L, M, O, tempL, tempM, tempO;
            Point2f N;

            vector <Point2f> src, dst; // src - Source Points basically the 4 end co-ordinates of the overlay image
            // dst - Destination Points to transform overlay image

            Mat warp_matrix;

            cv_getVertices(contours, top, slope, tempL);
            cv_getVertices(contours, right, slope, tempM);
            cv_getVertices(contours, bottom, slope, tempO);

            cv_updateCornerOr(orientation, tempL, L); // Re-arrange marker corners w.r.t orientation of the QR code
            cv_updateCornerOr(orientation, tempM, M); // Re-arrange marker corners w.r.t orientation of the QR code
            cv_updateCornerOr(orientation, tempO, O); // Re-arrange marker corners w.r.t orientation of the QR code

            float d1 = cv_distance(L[0], M[1]);
            float d2 = cv_distance(L[0], O[3]);
            float pomer=d1 / d2;
            float pomer2=d2 / d1;
            LOGD("colorQRreader: d1=%.2f, d2=%.2f, pomer=%.2f, pomer2=%.2f", d1,d2,pomer,pomer2);
            sstm<<"pomer: "<<pomer<<" pomer2: "<<pomer2<<endl;
            if (d1 / d2 < 0.9 || d2 / d1 < 0.9) {
                sstm << "zly marker" << endl;
                string result = sstm.str();
                env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
                return env->NewStringUTF(result.c_str());
            }
            if(true) {
                std::stringstream strim;
                strim<<"/storage/sdcard1/Download/chybne/chybne_"<<hintID<<".png";
                string res=strim.str();
                LOGD("saving image: /storage/sdcard1/Download/chybne/chybne_%d.png", hintID);
                imwrite(res,image);
                double intersectionCas = (double) getTickCount();
                int iflag = getIntersectionPoint(M[1], M[2], O[3], O[2], N, image, thr);
                intersectionCas = ((double) getTickCount() - intersectionCas) / getTickFrequency();
                sstm << "find intersection time: " << intersectionCas << endl;
                string result = sstm.str();
                env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
                return env->NewStringUTF(result.c_str());
            }
            float xOutDist = cv_distance(L[0], M[1]);
            qr_raw = Mat::zeros(xOutDist, xOutDist, CV_8UC3);

            src.push_back(L[0]);
            src.push_back(M[1]);
            src.push_back(N);
            src.push_back(O[3]);

            dst.push_back(Point2f(0, 0));
            dst.push_back(Point2f(qr_raw.cols, 0));
            dst.push_back(Point2f(qr_raw.cols, qr_raw.rows));
            dst.push_back(Point2f(0, qr_raw.rows));

            if (src.size() == 4 && dst.size() == 4) { // Failsafe for WarpMatrix Calculation to have only 4 Points with src and dst
                warp_matrix = getPerspectiveTransform(src, dst);
                warpPerspective(image, qr_raw, warp_matrix, Size(qr_raw.cols, qr_raw.rows));
            }

            //Draw contours on the image
            drawContours(image, contours, top, Scalar(255, 200, 0), 4, 8, hierarchy, 0);
            drawContours(image, contours, right, Scalar(0, 0, 255), 4, 8, hierarchy, 0);
            drawContours(image, contours, bottom, Scalar(255, 0, 100), 4, 8, hierarchy, 0);

            perspectiveTransform(L, L, warp_matrix);
            perspectiveTransform(M, M, warp_matrix);
            perspectiveTransform(O, O, warp_matrix);

            // get elementSize from marker size
            int COLUMNS;
            int ROWS;
            double elementSize;
            if (rowsHint == -1) {
                elementSize = cv_distance(L[0], L[1]) / 7;
                int c = round(qr_raw.cols / elementSize);
                elementSize = qr_raw.cols / (double) c;
                COLUMNS = qr_raw.cols / elementSize;
                ROWS = qr_raw.rows / elementSize;
            } else {
                elementSize = qr_raw.cols / (double) rowsHint;
                COLUMNS = qr_raw.cols / elementSize;
                ROWS = qr_raw.rows / elementSize;
            }
            sstm << "ROWS: " << ROWS << " COLUMNS: " << COLUMNS<< " qr_raw.cols: " << qr_raw.cols << " elementSize: " << elementSize << endl;
            imwrite("/storage/sdcard1/Download/qr_raw.png",qr_raw);
            // nacitame datove elementy
            int dataElementsSize = ROWS * COLUMNS - 3 * 8 * 8 - 6 - 4;
            int data[dataElementsSize];
            readDataFromQR(data, qr_raw, ROWS, COLUMNS, elementSize);
            sstm << "pocet datovych elementov: " << dataElementsSize << endl;

            // dekodovanie dat
            char bits[dataElementsSize * 3 + 1];
            bits[dataElementsSize * 3] = '\0';
            for (int i = 0; i < dataElementsSize; i++) {
                int f = data[i];
                int b = (f & 0x000000FF);
                int g = (f & 0x0000FF00) >> 8;
                int r = (f & 0x00FF0000) >> 16;
                if (b == 255) {
                    b = 16;
                } else {
                    b = b / 16;
                }
                if (g == 255) {
                    g = 16;
                } else {
                    g = g / 16;
                }
                if (r == 255) {
                    r = 16;
                } else {
                    r = r / 16;
                }
                b=b*17*17+g*17+r;
                string bity = getBitsFromIntColor(klasifikator[b]);
                bits[i * 3] = bity[0];
                bits[i * 3 + 1] = bity[1];
                bits[i * 3 + 2] = bity[2];

            }
//            sstm << "ziskane data: ";
//            for(int i=0; i<100; i++){
//               sstm << bits[i];
//            }
//            sstm<<endl;
//            sstm << "debug vypis farieb" << endl;
//            for (int i = 0; i < 20; i++) {
//                char b[3];
//                b[0] = bits[i * 3];
//                b[1] = bits[i * 3 + 1];
//                b[2] = bits[i * 3 + 2];
//                sstm << getFarba(b) << ",";
//            }
//            sstm << endl;
//            char skuskaBity[25]="000001010011100101110111";
//            sstm << "skuska vypis farieb" << endl;
//            for (int i = 0; i < 8; i++) {
//                char b[3];
//                b[0] = skuskaBity[i * 3];
//                b[1] = skuskaBity[i * 3 + 1];
//                b[2] = skuskaBity[i * 3 + 2];
//                sstm << getFarba(b) << ",";
//            }
//            sstm<<endl;
//
//            sstm<< "klasifikator[0] = "<<klasifikator[0]<<endl;
//            sstm<< "klasifikator[4297] = "<<klasifikator[4297]<<endl;
//            sstm<< "klasifikator[289] = "<<klasifikator[289]<<endl;
//            sstm<< "klasifikator[3536] = "<<klasifikator[3536]<<endl;
//            sstm<< "klasifikator[306] = "<<klasifikator[306]<<endl;
            int frameLength = 0;
            for (int i = 0; i < 16; i++) {
                if (bits[i] == '1') {
                    frameLength |= 1 << (16 - i - 1);
                }
            }

            sstm << "frameLength: " << frameLength << endl;
            if (frameLength > (dataElementsSize * 3) / 8 || frameLength < 10) {
                // zle precitana hlavicka, prilis vela dat
                sstm << "ZLE PRECITANA HLAVICKA frameLength: " << frameLength << endl;
                string result = sstm.str();
                env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
                return env->NewStringUTF(result.c_str());
            }

            // nacitame frame data
            char frame[frameLength];
            for (int i = 0; i < frameLength; i++) {
                frame[i] = 0x00;
                for (int k = 0; k < 8; k++) {
                    if (bits[16 + i * 8 + k] == '1') {
                        frame[i] |= 1 << (8 - k - 1);
                    }
                }
            }
            int frID = 0;
            frID |= (frame[0] & 0xFF) << 8;
            frID |= (frame[1] & 0xFF);
            int vsetkych = 0;
            vsetkych |= (frame[2] & 0xFF) << 8;
            vsetkych |= (frame[3] & 0xFF);
            int fps = 0;
            fps |= (frame[4] & 0xFF);
            sstm << "paket header: " << frID << "/" << vsetkych << "/" << fps << endl;
            // ak sme uz mali uspesne nacitany qrKod tak vieme pridat tuto dodatocnu kontrolu
            if (VSETKYCH != -1) {
                if (frID < 1 || frID > VSETKYCH) {
                    sstm << "zle nacitany frID" << endl;
                    string result = sstm.str();
                    env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
                    return env->NewStringUTF(result.c_str());
                }
                if (vsetkych != VSETKYCH) {
                    sstm << "zle nacitany vsetkych" << endl;
                    string result = sstm.str();
                    env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
                    return env->NewStringUTF(result.c_str());
                }
            }

            // hashe
            int qrHash = 0;
            qrHash |= (frame[frameLength - 4] & 0x000000FF) << 24;
            qrHash |= (frame[frameLength - 3] & 0x000000FF) << 16;
            qrHash |= (frame[frameLength - 2] & 0x000000FF) << 8;
            qrHash |= (frame[frameLength - 1] & 0x000000FF);
            int hashCode2 = hashCode(frame, frameLength - 4);
            sstm << "hash nacitany z qr kodu: " << qrHash << " ?== " << hashCode2 << endl;

            if (qrHash - hashCode2 == 0) {
                sstm << "uspesne nacitany kod" << endl;
                if (rowsHint == -1) {
                    rowsHint = ROWS;
                }
                if (VSETKYCH == -1) {
                    VSETKYCH = vsetkych;
                }
            } else {
                sstm << "NEuspesne nacitany kod" << endl;
            }

            string result = sstm.str();
            env->ReleaseIntArrayElements(*iArray, klasifikator, 0);
            return env->NewStringUTF(result.c_str());
        }
    }
}

}
import cv2
import numpy as np

# read
img = cv2.imread('fish.jpg',1)

# Increase contrast
img_temp = img
clahe = cv2.createCLAHE(clipLimit=3., tileGridSize=(8,8))
lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
l, a, b = cv2.split(lab)
l2 = clahe.apply(l)
lab = cv2.merge((l2,a,b))
img_temp = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)

# Remove some noise
kernel = np.ones((3,3), np.uint8)
img_temp = cv2.erode(img_temp, kernel, iterations=1) 
img_temp = cv2.GaussianBlur(img_temp, (3,3), cv2.BORDER_DEFAULT)

# Canny
img_canny = cv2.Canny(img_temp, 0, 255)
img_canny = cv2.dilate(img_canny, kernel, iterations=25)                                             # [ FINAL : img_canny ]

# Find adaptive threshold
img_at = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
img_at = cv2.adaptiveThreshold(img_at,255,cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 55, 25)    # VAR this last parameter adjustable. The lower, the thicker
img_orig_at = img_at
img_at = cv2.bitwise_not(img_at)                                                                    # [ FINAL : img_at ]

# Image subtraction to remove outlier noises
img_sub = cv2.bitwise_and(img_at, img_canny)                                                        # [ FINAL : img_sub ]

# Final image clean up
nb_components, output, stats, centroids = cv2.connectedComponentsWithStats(img_sub, connectivity=8)
sizes = []
for i in range(1, len(stats)):
    sizes.append(stats[i][4])
nb_components = nb_components - 1
min_size = 50                                                                                       # VAR adjust this pixel threshold. Removing blobs
img_final = np.zeros((img_sub.shape), dtype=np.uint8)
for i in range(0, nb_components):
    if sizes[i] >= min_size:
        img_final[output == i + 1] = 255
img_final = cv2.bitwise_not(img_final)                                                              # [ FINAL : img_final ]

# show/save
#cv2.namedWindow('final2', cv2.WINDOW_NORMAL)
cv2.imshow('final', img_final)
cv2.waitKey(0)